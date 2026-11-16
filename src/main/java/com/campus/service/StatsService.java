package com.campus.service;

import com.campus.dao.*;

import java.util.*;

/**
 * 统计业务逻辑层
 */
public final class StatsService {
    private static final StatsService INSTANCE = new StatsService();
    private StatsService() {}
    public static StatsService getInstance() { return INSTANCE; }

    public Map<String, Object> getStats() throws Exception {
        var stats = new LinkedHashMap<String, Object>();
        stats.put("totalUsers", UserDao.getInstance().countAll());
        stats.put("totalActivities", ActivityDao.getInstance().countAll());
        var pool = com.campus.config.DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var stmt = conn.createStatement()) {
                stats.put("totalSignups", stmt.executeQuery("SELECT COUNT(*) FROM signups").getInt(1));
                stats.put("totalRatings", stmt.executeQuery("SELECT COUNT(*) FROM ratings").getInt(1));
                stats.put("totalPromotions", stmt.executeQuery("SELECT COUNT(*) FROM promotions").getInt(1));
            }
            var categoryCount = new LinkedHashMap<String, Integer>();
            try (var ps = conn.prepareStatement("SELECT category, COUNT(*) as cnt FROM activities GROUP BY category")) {
                var rs = ps.executeQuery();
                while (rs.next()) categoryCount.put(rs.getString("category"), rs.getInt("cnt"));
            }
            stats.put("categoryCount", categoryCount);
            var vipCount = new LinkedHashMap<String, Integer>();
            try (var ps = conn.prepareStatement("SELECT vip_level, COUNT(*) as cnt FROM users GROUP BY vip_level")) {
                var rs = ps.executeQuery();
                while (rs.next()) vipCount.put(rs.getString("vip_level"), rs.getInt("cnt"));
            }
            stats.put("vipCount", vipCount);
        } finally { pool.release(conn); }
        return stats;
    }
}
