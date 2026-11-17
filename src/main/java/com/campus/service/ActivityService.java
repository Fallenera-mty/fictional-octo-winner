package com.campus.service;

import com.campus.dao.*;
import com.campus.dataobject.ActivityDO;
import com.campus.model.ApiException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 活动业务逻辑层
 */
public final class ActivityService {
    private static final ActivityService INSTANCE = new ActivityService();
    private ActivityService() {}
    public static ActivityService getInstance() { return INSTANCE; }

    private static final Map<String, Integer> PROMOTE_PRICES = Map.of("basic", 50, "premium", 120, "ultimate", 200);

    /** 活动默认时长（小时）：未填写结束时间时按此估算 */
    private static final int ACTIVITY_DURATION_HOURS = 2;
    private static final java.time.format.DateTimeFormatter HHMM =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    // ==================== 现场签到 ====================
    /** 签到码有效期：2 小时，组织者无需反复生成 */
    private static final long CHECKIN_CODE_TTL_MS = 2 * 60 * 60 * 1000L;
    /** 签到时间窗：活动开始前 1 小时开放，活动结束后 2 小时内仍可补签 */
    private static final long CHECKIN_EARLY_MINUTES = 60;
    private static final long CHECKIN_LATE_MINUTES = 120;
    /** 进程级签名密钥：重启后旧签到码自动失效，符合"现场短时有效"的定位 */
    private static final byte[] CHECKIN_SECRET = new byte[32];
    static { new SecureRandom().nextBytes(CHECKIN_SECRET); }

    /**
     * 发布者生成签到码，格式 NF-活动ID-过期秒级时间戳-签名
     */
    public Map<String, Object> createCheckinCode(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (!act.publisher.equals(account)) throw ApiException.forbidden("只有活动发布者可以生成签到码");

        long expireSec = (System.currentTimeMillis() + CHECKIN_CODE_TTL_MS) / 1000;
        String payload = activityId + "-" + expireSec;
        String code = "NF-" + payload + "-" + sign(payload);

        var resp = new LinkedHashMap<String, Object>();
        resp.put("code", code);
        resp.put("expireAt", expireSec * 1000);
        return resp;
    }

    /**
     * 参与者凭签到码签到：需已报名、且仅限活动当天
     */
    public Map<String, Object> checkin(String account, int activityId, String code) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (act.status == 0) throw ApiException.badRequest("活动已被平台下架，无法签到");
        String signupStatus = ActivityDao.getInstance().getSignupStatus(activityId, account);
        if ("waitlist".equals(signupStatus))
            throw ApiException.badRequest("你还在候补队列中，递补成功后才可签到");
        if (!"confirmed".equals(signupStatus))
            throw ApiException.badRequest("请先报名该活动，再完成现场签到");
        if (!isWithinCheckinWindow(act))
            throw ApiException.badRequest("当前不在签到时间内（活动开始前 1 小时至结束后 2 小时）");
        if (!isValidCheckinCode(activityId, code))
            throw ApiException.badRequest("签到码无效或已过期，请向发布者重新获取");

        boolean first = ActivityDao.getInstance().markCheckedIn(activityId, account);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("checkedIn", true);
        resp.put("firstTime", first);
        resp.put("checkedInCount", ActivityDao.getInstance().getCheckedInCount(activityId));
        return resp;
    }

    /**
     * 当前用户在该活动下的签到状态，供前端决定展示哪一块交互
     */
    public Map<String, Object> getCheckinStatus(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");

        boolean signedUp = ActivityDao.getInstance().hasSignedUp(activityId, account);
        long checkedInAt = signedUp ? ActivityDao.getInstance().getCheckedInAt(activityId, account) : 0;
        String signupStatus = signedUp ? ActivityDao.getInstance().getSignupStatus(activityId, account) : "";
        int waitlistPosition = "waitlist".equals(signupStatus)
                ? ActivityDao.getInstance().getWaitlistPosition(activityId, account) : 0;

        var resp = new LinkedHashMap<String, Object>();
        resp.put("signedUp", signedUp);
        resp.put("signupStatus", signupStatus == null ? "" : signupStatus);
        resp.put("waitlistPosition", waitlistPosition);
        resp.put("checkedIn", checkedInAt > 0);
        resp.put("checkedInAt", checkedInAt);
        resp.put("publisher", act.publisher.equals(account));
        resp.put("withinCheckinWindow", isWithinCheckinWindow(act));
        resp.put("started", hasStarted(act));
        resp.put("checkedInCount", ActivityDao.getInstance().getCheckedInCount(activityId));
        return resp;
    }

    private static String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CHECKIN_SECRET, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("签到码签名失败", e);
        }
    }

    private static boolean isValidCheckinCode(int activityId, String code) {
        if (code == null) return false;
        var parts = code.trim().toUpperCase(Locale.ROOT).split("-");
        if (parts.length != 4 || !"NF".equals(parts[0])) return false;
        try {
            if (Integer.parseInt(parts[1]) != activityId) return false;
            if (Long.parseLong(parts[2]) * 1000 < System.currentTimeMillis()) return false;
            String expect = sign(parts[1] + "-" + parts[2]);
            return MessageDigest.isEqual(
                    expect.getBytes(StandardCharsets.UTF_8),
                    parts[3].toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 活动是否已开始（日期+时间），时间格式异常时退化为按日期判断 */
    private static boolean hasStarted(ActivityDO act) {
        try {
            var start = LocalDateTime.of(LocalDate.parse(act.date), LocalTime.parse(act.time));
            return !LocalDateTime.now().isBefore(start);
        } catch (Exception e) {
            return act.date.compareTo(LocalDate.now().toString()) < 0;
        }
    }

    /** 签到时间窗：活动开始前 1 小时 ~ 活动结束后 2 小时 */
    private static boolean isWithinCheckinWindow(ActivityDO act) {
        try {
            var start = LocalDateTime.of(LocalDate.parse(act.date), LocalTime.parse(act.time));
            var end = LocalDateTime.of(LocalDate.parse(act.date), endTimeOf(act.time, act.endTime));
            var now = LocalDateTime.now();
            return !now.isBefore(start.minusMinutes(CHECKIN_EARLY_MINUTES))
                    && !now.isAfter(end.plusMinutes(CHECKIN_LATE_MINUTES));
        } catch (Exception e) {
            return LocalDate.now().toString().equals(act.date);
        }
    }

    /** 结束时间：优先取发布时填写的时间，缺省按开始 +2 小时（跨天截到 23:59） */
    private static LocalTime endTimeOf(String startTime, String endTime) {
        LocalTime start = parseTime(startTime);
        if (start == null) return LocalTime.of(23, 59);
        LocalTime end = parseTime(endTime);
        if (end != null && end.isAfter(start)) return end;
        LocalTime fallback = start.plusHours(ACTIVITY_DURATION_HOURS);
        return fallback.isAfter(start) ? fallback : LocalTime.of(23, 59);
    }

    private static String resolveEndTime(String startTime, String endTime) {
        LocalTime start = parseTime(startTime);
        LocalTime end = parseTime(endTime);
        if (end != null && (start == null || end.isAfter(start))) return end.format(HHMM);
        if (start == null) return "";
        LocalTime fallback = start.plusHours(ACTIVITY_DURATION_HOURS);
        return (fallback.isAfter(start) ? fallback : LocalTime.of(23, 59)).format(HHMM);
    }

    public List<Map<String, Object>> listActivities(String keyword, String category) throws Exception {
        return ActivityDao.getInstance().listByFilter(keyword, category);
    }

    public Map<String, Object> getActivityDetail(int id) throws Exception {
        var act = ActivityDao.getInstance().findById(id);
        if (act == null) throw ApiException.notFound("活动不存在");
        var map = act.toMap();
        enrichActivity(map, id);
        return map;
    }

    public Map<String, Object> createActivity(String account, String name, String category, String description,
            String date, String time, String endTime, String location, String school, int maxPeople, double fee,
            String tags, boolean promoted, String promotePlan, int promoteDays, int creditHours) throws Exception {
        if (name.isEmpty() || category.isEmpty() || date.isEmpty() || location.isEmpty() || school.isEmpty())
            throw ApiException.badRequest("请填写所有必填项");
        if (!endTime.isEmpty()) {
            if (parseTime(endTime) == null) throw ApiException.badRequest("结束时间格式不正确");
            LocalTime start = parseTime(time);
            if (start != null && !parseTime(endTime).isAfter(start))
                throw ApiException.badRequest("结束时间需晚于开始时间");
        }
        var act = new ActivityDO();
        act.name = name; act.category = category; act.description = description;
        act.date = date; act.time = time; act.endTime = resolveEndTime(time, endTime);
        act.location = location; act.school = school;
        act.maxPeople = maxPeople; act.fee = fee; act.publisher = account; act.tags = tags;
        act.promoted = promoted; act.promotePlan = promotePlan; act.promoteDays = promoteDays;
        act.creditHours = Math.max(0, Math.min(20, creditHours));
        int id = ActivityDao.getInstance().insert(act);
        if (promoted && !promotePlan.isEmpty() && promoteDays > 0) {
            var userService = UserService.getInstance();
            int unitPrice = PROMOTE_PRICES.getOrDefault(promotePlan, 50);
            double discount = userService.getPromoteDiscount(account);
            double total = unitPrice * promoteDays * discount;
            ActivityDao.getInstance().updatePromotion(id, promotePlan, promoteDays, total);
        }
        return getActivityDetail(id);
    }

    public void deleteActivity(String account, int id) throws Exception {
        var act = ActivityDao.getInstance().findById(id);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (!act.publisher.equals(account)) throw ApiException.forbidden("只能删除自己发布的活动");
        ActivityDao.getInstance().deleteById(id);
    }

    public Map<String, Object> signup(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (act.status == 0) throw ApiException.badRequest("活动已被平台下架，无法报名");
        String today = LocalDate.now().toString();
        if (act.date.compareTo(today) < 0)
            throw ApiException.badRequest("活动已开始或已结束，无法报名");
        if (act.date.equals(today)) {
            var end = LocalDateTime.of(LocalDate.parse(act.date), endTimeOf(act.time, act.endTime));
            if (LocalDateTime.now().isAfter(end))
                throw ApiException.badRequest("活动已结束，无法报名");
        }
        String existing = ActivityDao.getInstance().getSignupStatus(activityId, account);
        if ("waitlist".equals(existing)) throw ApiException.badRequest("你已在该活动的候补队列中");
        if (existing != null) throw ApiException.badRequest("已报名此活动");

        int count = ActivityDao.getInstance().getSignupCount(activityId);
        boolean full = act.maxPeople > 0 && count >= act.maxPeople;
        String status = full ? "waitlist" : "confirmed";
        ActivityDao.getInstance().addSignup(activityId, account, status);

        var resp = new LinkedHashMap<String, Object>();
        resp.put("status", status);
        resp.put("signupCount", ActivityDao.getInstance().getSignupCount(activityId));
        resp.put("waitlistCount", ActivityDao.getInstance().getWaitlistCount(activityId));
        if (full) resp.put("position", ActivityDao.getInstance().getWaitlistPosition(activityId, account));
        return resp;
    }

    public Map<String, Object> cancelSignup(String account, int activityId) throws Exception {
        String cancelled = ActivityDao.getInstance().removeSignup(activityId, account);
        if (cancelled == null) throw ApiException.badRequest("未报名此活动");

        // 正式名额释放后，自动把候补队列中最早的同学递补为正式报名
        String promoted = "confirmed".equals(cancelled)
                ? ActivityDao.getInstance().promoteNextWaitlist(activityId) : null;

        var resp = new LinkedHashMap<String, Object>();
        resp.put("signupCount", ActivityDao.getInstance().getSignupCount(activityId));
        resp.put("waitlistCount", ActivityDao.getInstance().getWaitlistCount(activityId));
        resp.put("promoted", promoted == null ? "" : promoted);
        return resp;
    }

    /**
     * 报名前的时间冲突检测：同一活动时间段（开始~结束）与本活动重叠即视为冲突
     */
    public List<Map<String, Object>> findConflicts(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");

        var conflicts = new ArrayList<Map<String, Object>>();
        LocalTime start = parseTime(act.time);
        if (start == null) return conflicts;
        LocalTime end = endTimeOf(act.time, act.endTime);
        for (var other : ActivityDao.getInstance().findSameDaySignups(account, activityId, act.date)) {
            LocalTime otherStart = parseTime((String) other.get("time"));
            if (otherStart == null) continue;
            LocalTime otherEnd = endTimeOf((String) other.get("time"), (String) other.get("endTime"));
            if (start.isBefore(otherEnd) && otherStart.isBefore(end)) conflicts.add(other);
        }
        return conflicts;
    }

    /**
     * 加入/移出「我的日历」，返回是否已加入
     */
    public boolean toggleCalendar(String account, int activityId) throws Exception {
        if (ActivityDao.getInstance().findById(activityId) == null) throw ApiException.notFound("活动不存在");
        return ActivityDao.getInstance().toggleCalendar(activityId, account);
    }

    public boolean isInCalendar(String account, int activityId) throws Exception {
        return ActivityDao.getInstance().isInCalendar(activityId, account);
    }

    public List<Map<String, Object>> getMyCalendar(String account) throws Exception {
        return ActivityDao.getInstance().listByCalendar(account);
    }

    private static LocalTime parseTime(String time) {
        try {
            return LocalTime.parse(time);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> rate(String account, int activityId, int stars, String comment) throws Exception {
        if (stars < 1 || stars > 5) throw ApiException.badRequest("评分需为1-5星");
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        String signupStatus = ActivityDao.getInstance().getSignupStatus(activityId, account);
        if (signupStatus == null) throw ApiException.badRequest("未报名此活动，无法评分");
        if (!"confirmed".equals(signupStatus)) throw ApiException.badRequest("候补状态暂不可评分，递补成功后再试");
        if (ActivityDao.getInstance().getCheckedInAt(activityId, account) <= 0)
            throw ApiException.badRequest("请先在活动现场完成签到，签到后才可评分");
        if (!hasStarted(act)) throw ApiException.badRequest("活动尚未开始，暂不可评分");
        ActivityDao.getInstance().addRating(activityId, account, stars, comment);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("avgRating", Math.round(ActivityDao.getInstance().getAvgRating(activityId) * 10.0) / 10.0);
        resp.put("ratingCount", ActivityDao.getInstance().getRatingCount(activityId));
        return resp;
    }

    public boolean toggleFavorite(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        return ActivityDao.getInstance().toggleFavorite(activityId, account);
    }

    public List<Map<String, Object>> getMyActivities(String account) throws Exception {
        return ActivityDao.getInstance().listByPublisher(account);
    }

    public List<Map<String, Object>> getMySignups(String account) throws Exception {
        return ActivityDao.getInstance().listBySignup(account);
    }

    public List<Map<String, Object>> getMyFavorites(String account) throws Exception {
        return ActivityDao.getInstance().listByFavorite(account);
    }

    public Map<String, Object> getUserStats(String account) throws Exception {
        return ActivityDao.getInstance().getUserStats(account);
    }

    public Map<String, Object> buyPromotion(String account, int activityId, String plan, int days) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (!act.publisher.equals(account)) throw ApiException.forbidden("只能为自己的活动购买推广");
        if (!PROMOTE_PRICES.containsKey(plan) || days <= 0) throw ApiException.badRequest("无效的推广方案或天数");
        var userService = UserService.getInstance();
        int unitPrice = PROMOTE_PRICES.get(plan);
        double discount = userService.getPromoteDiscount(account);
        double total = unitPrice * days * discount;
        ActivityDao.getInstance().updatePromotion(activityId, plan, days, total);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("plan", plan); resp.put("days", days); resp.put("unitPrice", unitPrice);
        resp.put("discount", discount); resp.put("totalCost", Math.round(total * 100.0) / 100.0);
        return resp;
    }

    private void enrichActivity(Map<String, Object> map, int id) throws Exception {
        var userService = UserService.getInstance();
        String publisher = (String) map.get("publisher");
        double fee = ((Number) map.get("fee")).doubleValue();
        double commissionRate = userService.getCommissionRate(publisher);
        double commission = fee * commissionRate;
        map.put("commissionRate", commissionRate);
        map.put("commission", Math.round(commission * 100.0) / 100.0);
        map.put("netIncome", Math.round((fee - commission) * 100.0) / 100.0);
        map.put("signupCount", ActivityDao.getInstance().getSignupCount(id));
        map.put("waitlistCount", ActivityDao.getInstance().getWaitlistCount(id));
        map.put("checkedInCount", ActivityDao.getInstance().getCheckedInCount(id));
        map.put("signupAccounts", ActivityDao.getInstance().getSignupAccounts(id));
        map.put("avgRating", Math.round(ActivityDao.getInstance().getAvgRating(id) * 10.0) / 10.0);
        map.put("ratingCount", ActivityDao.getInstance().getRatingCount(id));
        var ratingList = new ArrayList<Map<String, Object>>();
        for (var r : ActivityDao.getInstance().getRatings(id)) ratingList.add(r.toMap());
        map.put("ratings", ratingList);
    }

    // ==================== 管理员操作 ====================

    public List<Map<String, Object>> listAllForAdmin() throws Exception {
        return ActivityDao.getInstance().listAllForAdmin();
    }

    public void updateActivityStatus(int id, int status) throws Exception {
        var act = ActivityDao.getInstance().findById(id);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (status != 0 && status != 1) throw ApiException.badRequest("无效的状态值");
        ActivityDao.getInstance().updateStatus(id, status);
    }

    public void deleteActivityByAdmin(int id) throws Exception {
        if (ActivityDao.getInstance().findById(id) == null) throw ApiException.notFound("活动不存在");
        ActivityDao.getInstance().deleteById(id);
    }

    // ==================== 评论区 ====================

    public List<Map<String, Object>> listComments(int activityId) throws Exception {
        if (ActivityDao.getInstance().findById(activityId) == null) throw ApiException.notFound("活动不存在");
        return ActivityDao.getInstance().listComments(activityId);
    }

    public List<Map<String, Object>> addComment(String account, int activityId, String content) throws Exception {
        if (content == null || content.trim().isEmpty()) throw ApiException.badRequest("评论内容不能为空");
        if (content.trim().length() > 500) throw ApiException.badRequest("评论内容最多500字");
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        ActivityDao.getInstance().addComment(activityId, account, content.trim());
        return ActivityDao.getInstance().listComments(activityId);
    }
}
