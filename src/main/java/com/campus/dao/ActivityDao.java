package com.campus.dao;

import com.campus.config.DbPool;
import com.campus.dataobject.ActivityDO;
import com.campus.dataobject.RatingDO;

import java.sql.*;
import java.util.*;

/**
 * 活动数据访问层
 */
public final class ActivityDao {
    private static final ActivityDao INSTANCE = new ActivityDao();
    private ActivityDao() {}
    public static ActivityDao getInstance() { return INSTANCE; }

    public List<Map<String, Object>> listByFilter(String keyword, String category) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var sql = new StringBuilder("SELECT * FROM activities WHERE status=1");
            var params = new ArrayList<Object>();
            if (category != null && !category.isEmpty() && !"all".equals(category)) {
                sql.append(" AND category=?"); params.add(category);
            }
            if (keyword != null && !keyword.isEmpty()) {
                sql.append(" AND (LOWER(name) LIKE ? OR LOWER(school) LIKE ? OR LOWER(tags) LIKE ? OR LOWER(description) LIKE ?)");
                var kw = "%" + keyword.toLowerCase() + "%";
                for (int i = 0; i < 4; i++) params.add(kw);
            }
            sql.append(" ORDER BY promoted DESC, created_at DESC");
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = mapRow(rs).toMap();
                    m.put("signupCount", getSignupCount(((Number) m.get("id")).intValue()));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    public ActivityDO findById(int id) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT * FROM activities WHERE id=?")) {
                ps.setInt(1, id);
                var rs = ps.executeQuery();
                return rs.next() ? mapRow(rs) : null;
            }
        } finally { pool.release(conn); }
    }

    public int insert(ActivityDO act) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO activities(name,category,description,date,time,end_time,location,school,max_people,fee,publisher,tags,promoted,promote_plan,promote_days,credit_hours) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, act.name); ps.setString(2, act.category); ps.setString(3, act.description);
                ps.setString(4, act.date); ps.setString(5, act.time); ps.setString(6, act.endTime);
                ps.setString(7, act.location);
                ps.setString(8, act.school); ps.setInt(9, act.maxPeople); ps.setDouble(10, act.fee);
                ps.setString(11, act.publisher); ps.setString(12, act.tags);
                ps.setInt(13, act.promoted ? 1 : 0); ps.setString(14, act.promotePlan); ps.setInt(15, act.promoteDays);
                ps.setInt(16, act.creditHours);
                ps.executeUpdate();
                var rs = ps.getGeneratedKeys();
                return rs.next() ? rs.getInt(1) : -1;
            }
        } finally { pool.release(conn); }
    }

    public void deleteById(int id) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("DELETE FROM signups WHERE activity_id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
            try (var ps = conn.prepareStatement("DELETE FROM ratings WHERE activity_id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
            try (var ps = conn.prepareStatement("DELETE FROM favorites WHERE activity_id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
            try (var ps = conn.prepareStatement("DELETE FROM calendar_items WHERE activity_id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
            try (var ps = conn.prepareStatement("DELETE FROM promotions WHERE activity_id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
            try (var ps = conn.prepareStatement("DELETE FROM activities WHERE id=?")) { ps.setInt(1, id); ps.executeUpdate(); }
        } finally { pool.release(conn); }
    }

    // 报名
    public int getSignupCount(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM signups WHERE activity_id=? AND status='confirmed'")) {
                ps.setInt(1, activityId); return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    public int getWaitlistCount(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM signups WHERE activity_id=? AND status='waitlist'")) {
                ps.setInt(1, activityId); return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    /** 返回报名状态：confirmed / waitlist，未报名返回 null */
    public String getSignupStatus(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT status FROM signups WHERE activity_id=? AND account=?")) {
                ps.setInt(1, activityId); ps.setString(2, account);
                var rs = ps.executeQuery();
                return rs.next() ? rs.getString("status") : null;
            }
        } finally { pool.release(conn); }
    }

    /** 候补队列中的排位，从 1 开始；不在候补中返回 0 */
    public int getWaitlistPosition(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("""
                    SELECT COUNT(*) FROM signups
                    WHERE activity_id=? AND status='waitlist'
                      AND created_at <= (SELECT created_at FROM signups WHERE activity_id=? AND account=?)
                    """)) {
                ps.setInt(1, activityId); ps.setInt(2, activityId); ps.setString(3, account);
                return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    public boolean hasSignedUp(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT 1 FROM signups WHERE activity_id=? AND account=?")) {
                ps.setInt(1, activityId); ps.setString(2, account); return ps.executeQuery().next();
            }
        } finally { pool.release(conn); }
    }

    public int addSignup(int activityId, String account, String status) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("INSERT INTO signups(activity_id,account,status) VALUES(?,?,?)")) {
                ps.setInt(1, activityId); ps.setString(2, account); ps.setString(3, status);
                ps.executeUpdate();
            }
            return getSignupCount(activityId);
        } finally { pool.release(conn); }
    }

    /** 取消报名，返回取消前的状态；未报名返回 null */
    public String removeSignup(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            String status;
            try (var ps = conn.prepareStatement("SELECT status FROM signups WHERE activity_id=? AND account=?")) {
                ps.setInt(1, activityId); ps.setString(2, account);
                var rs = ps.executeQuery();
                if (!rs.next()) return null;
                status = rs.getString("status");
            }
            try (var ps = conn.prepareStatement("DELETE FROM signups WHERE activity_id=? AND account=?")) {
                ps.setInt(1, activityId); ps.setString(2, account);
                ps.executeUpdate();
            }
            return status;
        } finally { pool.release(conn); }
    }

    /** 把候补队列中最早的一位递补为正式报名，返回其账号；无人候补返回 null */
    public String promoteNextWaitlist(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            String account = null;
            try (var ps = conn.prepareStatement("""
                    SELECT account FROM signups
                    WHERE activity_id=? AND status='waitlist'
                    ORDER BY created_at ASC, rowid ASC LIMIT 1
                    """)) {
                ps.setInt(1, activityId);
                var rs = ps.executeQuery();
                if (rs.next()) account = rs.getString("account");
            }
            if (account == null) return null;
            try (var ps = conn.prepareStatement(
                    "UPDATE signups SET status='confirmed' WHERE activity_id=? AND account=?")) {
                ps.setInt(1, activityId); ps.setString(2, account);
                ps.executeUpdate();
            }
            return account;
        } finally { pool.release(conn); }
    }

    /** 用户在同一天已确认报名的其他活动，用于报名时的冲突提示 */
    public List<Map<String, Object>> findSameDaySignups(String account, int excludeId, String date) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement("""
                    SELECT a.id, a.name, a.date, a.time, a.end_time FROM activities a JOIN signups s ON a.id=s.activity_id
                    WHERE s.account=? AND s.status='confirmed' AND a.status=1 AND a.date=? AND a.id<>?
                    """)) {
                ps.setString(1, account); ps.setString(2, date); ps.setInt(3, excludeId);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", rs.getInt("id")); m.put("name", rs.getString("name"));
                    m.put("date", rs.getString("date")); m.put("time", rs.getString("time"));
                    m.put("endTime", rs.getString("end_time"));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    public List<String> getSignupAccounts(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<String>();
            try (var ps = conn.prepareStatement(
                    "SELECT account FROM signups WHERE activity_id=? AND status='confirmed'")) {
                ps.setInt(1, activityId);
                var rs = ps.executeQuery();
                while (rs.next()) list.add(rs.getString("account"));
            }
            return list;
        } finally { pool.release(conn); }
    }

    /** 返回签到时间戳（毫秒），未签到返回 0 */
    public long getCheckedInAt(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try (var ps = conn.prepareStatement("SELECT checked_in_at FROM signups WHERE activity_id=? AND account=?")) {
            ps.setInt(1, activityId); ps.setString(2, account);
            var rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } finally { pool.release(conn); }
    }

    /** 标记签到，返回是否为首次签到 */
    public boolean markCheckedIn(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try (var ps = conn.prepareStatement(
                "UPDATE signups SET checked_in_at=? WHERE activity_id=? AND account=? AND status='confirmed' AND checked_in_at IS NULL")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, activityId); ps.setString(3, account);
            return ps.executeUpdate() > 0;
        } finally { pool.release(conn); }
    }

    public int getCheckedInCount(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try (var ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM signups WHERE activity_id=? AND checked_in_at IS NOT NULL")) {
            ps.setInt(1, activityId); return ps.executeQuery().getInt(1);
        } finally { pool.release(conn); }
    }

    // 评分
    public void addRating(int activityId, String account, int stars, String comment) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("INSERT INTO ratings(activity_id,account,stars,comment) VALUES(?,?,?,?)")) {
                ps.setInt(1, activityId); ps.setString(2, account); ps.setInt(3, stars); ps.setString(4, comment);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public List<RatingDO> getRatings(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<RatingDO>();
            try (var ps = conn.prepareStatement("SELECT account,stars,comment FROM ratings WHERE activity_id=?")) {
                ps.setInt(1, activityId);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var r = new RatingDO();
                    r.account = rs.getString("account"); r.stars = rs.getInt("stars"); r.comment = rs.getString("comment");
                    list.add(r);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    public double getAvgRating(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT AVG(stars) FROM ratings WHERE activity_id=?")) {
                ps.setInt(1, activityId); var rs = ps.executeQuery(); return rs.next() ? rs.getDouble(1) : 0;
            }
        } finally { pool.release(conn); }
    }

    public int getRatingCount(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM ratings WHERE activity_id=?")) {
                ps.setInt(1, activityId); return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    // 收藏
    public boolean toggleFavorite(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            boolean exists;
            try (var ps = conn.prepareStatement("SELECT 1 FROM favorites WHERE account=? AND activity_id=?")) {
                ps.setString(1, account); ps.setInt(2, activityId); exists = ps.executeQuery().next();
            }
            if (exists) {
                try (var ps = conn.prepareStatement("DELETE FROM favorites WHERE account=? AND activity_id=?")) {
                    ps.setString(1, account); ps.setInt(2, activityId); ps.executeUpdate();
                }
                return false;
            } else {
                try (var ps = conn.prepareStatement("INSERT INTO favorites(account,activity_id) VALUES(?,?)")) {
                    ps.setString(1, account); ps.setInt(2, activityId); ps.executeUpdate();
                }
                return true;
            }
        } finally { pool.release(conn); }
    }

    // 我的日历：加入/移出、是否已加入、按活动时间排序
    public boolean toggleCalendar(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            boolean exists;
            try (var ps = conn.prepareStatement("SELECT 1 FROM calendar_items WHERE account=? AND activity_id=?")) {
                ps.setString(1, account); ps.setInt(2, activityId); exists = ps.executeQuery().next();
            }
            if (exists) {
                try (var ps = conn.prepareStatement("DELETE FROM calendar_items WHERE account=? AND activity_id=?")) {
                    ps.setString(1, account); ps.setInt(2, activityId); ps.executeUpdate();
                }
                return false;
            } else {
                try (var ps = conn.prepareStatement("INSERT INTO calendar_items(account,activity_id) VALUES(?,?)")) {
                    ps.setString(1, account); ps.setInt(2, activityId); ps.executeUpdate();
                }
                return true;
            }
        } finally { pool.release(conn); }
    }

    public boolean isInCalendar(int activityId, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT 1 FROM calendar_items WHERE account=? AND activity_id=?")) {
                ps.setString(1, account); ps.setInt(2, activityId);
                return ps.executeQuery().next();
            }
        } finally { pool.release(conn); }
    }

    public List<Map<String, Object>> listByCalendar(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement(
                    "SELECT a.* FROM activities a JOIN calendar_items c ON a.id=c.activity_id "
                  + "WHERE c.account=? ORDER BY a.date ASC, a.time ASC")) {
                ps.setString(1, account);
                var rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs).toMap());
            }
            return list;
        } finally { pool.release(conn); }
    }

    // 我的活动/报名/收藏
    public List<Map<String, Object>> listByPublisher(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement("SELECT * FROM activities WHERE publisher=? ORDER BY created_at DESC")) {
                ps.setString(1, account);
                var rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs).toMap());
            }
            return list;
        } finally { pool.release(conn); }
    }

    public List<Map<String, Object>> listBySignup(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement(
                    "SELECT a.* FROM activities a JOIN signups s ON a.id=s.activity_id WHERE s.account=? ORDER BY a.created_at DESC")) {
                ps.setString(1, account);
                var rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs).toMap());
            }
            return list;
        } finally { pool.release(conn); }
    }

    public List<Map<String, Object>> listByFavorite(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement(
                    "SELECT a.* FROM activities a JOIN favorites f ON a.id=f.activity_id WHERE f.account=? ORDER BY a.created_at DESC")) {
                ps.setString(1, account);
                var rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs).toMap());
            }
            return list;
        } finally { pool.release(conn); }
    }

    // 个人学时与出勤统计
    public Map<String, Object> getUserStats(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var stats = new LinkedHashMap<String, Object>();
            try (var ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(a.credit_hours),0) AS hours, COUNT(*) AS attended "
                  + "FROM signups s JOIN activities a ON a.id=s.activity_id "
                  + "WHERE s.account=? AND s.checked_in_at IS NOT NULL")) {
                ps.setString(1, account);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("totalHours", rs.getInt("hours"));
                        stats.put("attended", rs.getInt("attended"));
                    }
                }
            }
            String today = java.time.LocalDate.now().toString();
            String nowTime = java.time.LocalTime.now().toString();
            if (nowTime.length() > 5) nowTime = nowTime.substring(0, 5);
            try (var ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM signups s JOIN activities a ON a.id=s.activity_id "
                  + "WHERE s.account=? AND s.checked_in_at IS NULL "
                  + "AND COALESCE(s.status,'confirmed')='confirmed' "
                  + "AND (a.date < ? OR (a.date = ? AND "
                  + "CASE WHEN a.end_time IS NOT NULL AND a.end_time<>'' THEN a.end_time "
                  + "ELSE strftime('%H:%M', datetime(a.date || ' ' || a.time, '+2 hours')) END < ?))")) {
                ps.setString(1, account);
                ps.setString(2, today);
                ps.setString(3, today);
                ps.setString(4, nowTime);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) stats.put("absent", rs.getInt(1));
                }
            }
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM signups WHERE account=?")) {
                ps.setString(1, account);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) stats.put("totalSignups", rs.getInt(1));
                }
            }
            return stats;
        } finally { pool.release(conn); }
    }

    // 推广
    public void updatePromotion(int activityId, String plan, int days, double totalCost) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("UPDATE activities SET promoted=1, promote_plan=?, promote_days=? WHERE id=?")) {
                ps.setString(1, plan); ps.setInt(2, days); ps.setInt(3, activityId); ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO promotions(activity_id,plan,days,total_cost) VALUES(?,?,?,?)")) {
                ps.setInt(1, activityId); ps.setString(2, plan); ps.setInt(3, days); ps.setDouble(4, totalCost); ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    // 统计
    public int countAll() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var stmt = conn.createStatement()) { return stmt.executeQuery("SELECT COUNT(*) FROM activities").getInt(1); }
        } finally { pool.release(conn); }
    }

    public int countByCategory(String category) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM activities WHERE category=?")) {
                ps.setString(1, category); return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    // ==================== 管理端 ====================

    public List<Map<String, Object>> listAllForAdmin() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement("SELECT * FROM activities ORDER BY created_at DESC")) {
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = mapRow(rs).toMap();
                    m.put("signupCount", getSignupCount(((Number) m.get("id")).intValue()));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    public void updateStatus(int id, int status) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("UPDATE activities SET status=? WHERE id=?")) {
                ps.setInt(1, status); ps.setInt(2, id);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    // ==================== 评论区 ====================

    public void addComment(int activityId, String account, String content) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("INSERT INTO comments(activity_id,account,content) VALUES(?,?,?)")) {
                ps.setInt(1, activityId); ps.setString(2, account); ps.setString(3, content);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public List<Map<String, Object>> listComments(int activityId) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement(
                    "SELECT c.id, c.account, c.content, c.created_at, u.nickname FROM comments c LEFT JOIN users u ON c.account=u.account WHERE c.activity_id=? ORDER BY c.created_at ASC")) {
                ps.setInt(1, activityId);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", rs.getInt("id"));
                    m.put("account", rs.getString("account"));
                    String nickname = rs.getString("nickname");
                    m.put("nickname", nickname != null ? nickname : rs.getString("account"));
                    m.put("content", rs.getString("content"));
                    m.put("createdAt", rs.getLong("created_at"));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    // ==================== 智能推荐 ====================

    /**
     * 统计用户报名+收藏活动的类别分布，返回最感兴趣的类别
     */
    public String topCategory(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("""
                    SELECT category, COUNT(*) as cnt FROM (
                        SELECT a.category FROM activities a JOIN signups s ON a.id=s.activity_id WHERE s.account=?
                        UNION ALL
                        SELECT a.category FROM activities a JOIN favorites f ON a.id=f.activity_id WHERE f.account=?
                    ) GROUP BY category ORDER BY cnt DESC LIMIT 1
                    """)) {
                ps.setString(1, account); ps.setString(2, account);
                var rs = ps.executeQuery();
                return rs.next() ? rs.getString("category") : null;
            }
        } finally { pool.release(conn); }
    }

    /**
     * 按偏好类别推荐：未报名、未收藏、未过期、已上架的活动
     */
    public List<Map<String, Object>> recommendByCategory(String category, String account, String today, int limit) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement("""
                    SELECT a.* FROM activities a
                    WHERE a.category=? AND a.status=1 AND a.date>=?
                      AND a.id NOT IN (SELECT activity_id FROM signups WHERE account=?)
                      AND a.id NOT IN (SELECT activity_id FROM favorites WHERE account=?)
                    ORDER BY a.promoted DESC, a.created_at DESC LIMIT ?
                    """)) {
                ps.setString(1, category); ps.setString(2, today);
                ps.setString(3, account); ps.setString(4, account); ps.setInt(5, limit);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = mapRow(rs).toMap();
                    m.put("signupCount", getSignupCount(((Number) m.get("id")).intValue()));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    /**
     * 无偏好时的兜底推荐：最新上架且未报名的活动
     */
    public List<Map<String, Object>> listLatestForRecommend(String account, String today, int limit) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<Map<String, Object>>();
            try (var ps = conn.prepareStatement("""
                    SELECT a.* FROM activities a
                    WHERE a.status=1 AND a.date>=?
                      AND a.id NOT IN (SELECT activity_id FROM signups WHERE account=?)
                    ORDER BY a.created_at DESC LIMIT ?
                    """)) {
                ps.setString(1, today); ps.setString(2, account); ps.setInt(3, limit);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    var m = mapRow(rs).toMap();
                    m.put("signupCount", getSignupCount(((Number) m.get("id")).intValue()));
                    list.add(m);
                }
            }
            return list;
        } finally { pool.release(conn); }
    }

    private ActivityDO mapRow(ResultSet rs) throws SQLException {
        var a = new ActivityDO();
        a.id = rs.getInt("id"); a.name = rs.getString("name"); a.category = rs.getString("category");
        a.description = rs.getString("description"); a.date = rs.getString("date"); a.time = rs.getString("time");
        a.endTime = rs.getString("end_time");
        a.location = rs.getString("location"); a.school = rs.getString("school");
        a.maxPeople = rs.getInt("max_people"); a.fee = rs.getDouble("fee");
        a.creditHours = rs.getInt("credit_hours");
        a.publisher = rs.getString("publisher"); a.tags = rs.getString("tags");
        a.promoted = rs.getInt("promoted") == 1; a.promotePlan = rs.getString("promote_plan");
        a.promoteDays = rs.getInt("promote_days"); a.createdAt = rs.getLong("created_at");
        a.status = rs.getInt("status");
        return a;
    }
}
