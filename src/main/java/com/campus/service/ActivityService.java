package com.campus.service;

import com.campus.dao.*;
import com.campus.dataobject.ActivityDO;
import com.campus.model.ApiException;

import java.util.*;

/**
 * 活动业务逻辑层
 */
public final class ActivityService {
    private static final ActivityService INSTANCE = new ActivityService();
    private ActivityService() {}
    public static ActivityService getInstance() { return INSTANCE; }

    private static final Map<String, Integer> PROMOTE_PRICES = Map.of("basic", 50, "premium", 120, "ultimate", 200);

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
            String date, String time, String location, String school, int maxPeople, double fee,
            String tags, boolean promoted, String promotePlan, int promoteDays) throws Exception {
        if (name.isEmpty() || category.isEmpty() || date.isEmpty() || location.isEmpty() || school.isEmpty())
            throw ApiException.badRequest("请填写所有必填项");
        var act = new ActivityDO();
        act.name = name; act.category = category; act.description = description;
        act.date = date; act.time = time; act.location = location; act.school = school;
        act.maxPeople = maxPeople; act.fee = fee; act.publisher = account; act.tags = tags;
        act.promoted = promoted; act.promotePlan = promotePlan; act.promoteDays = promoteDays;
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

    public int signup(String account, int activityId) throws Exception {
        var act = ActivityDao.getInstance().findById(activityId);
        if (act == null) throw ApiException.notFound("活动不存在");
        if (act.status == 0) throw ApiException.badRequest("活动已被平台下架，无法报名");
        String today = java.time.LocalDate.now().toString();
        if (act.date.compareTo(today) < 0)
            throw ApiException.badRequest("活动已开始或已结束，无法报名");
        if (ActivityDao.getInstance().hasSignedUp(activityId, account))
            throw ApiException.badRequest("已报名此活动");
        int count = ActivityDao.getInstance().getSignupCount(activityId);
        if (act.maxPeople > 0 && count >= act.maxPeople)
            throw ApiException.badRequest("活动人数已满");
        return ActivityDao.getInstance().addSignup(activityId, account);
    }

    public int cancelSignup(String account, int activityId) throws Exception {
        int result = ActivityDao.getInstance().removeSignup(activityId, account);
        if (result < 0) throw ApiException.badRequest("未报名此活动");
        return result;
    }

    public Map<String, Object> rate(String account, int activityId, int stars, String comment) throws Exception {
        if (stars < 1 || stars > 5) throw ApiException.badRequest("评分需为1-5星");
        if (!ActivityDao.getInstance().hasSignedUp(activityId, account))
            throw ApiException.badRequest("未报名此活动，无法评分");
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
