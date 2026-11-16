package com.campus.service;

import com.campus.dao.ActivityDao;

import java.util.*;

/**
 * 推荐业务逻辑层 - 基于用户行为的个性化推荐
 * 偏好来源：报名记录 + 收藏记录的类别分布统计
 */
public final class RecommendService {
    private static final RecommendService INSTANCE = new RecommendService();
    private RecommendService() {}
    public static RecommendService getInstance() { return INSTANCE; }

    private static final int RECOMMEND_LIMIT = 4;

    public Map<String, Object> recommend(String account) throws Exception {
        var dao = ActivityDao.getInstance();
        String today = java.time.LocalDate.now().toString();

        String topCategory = dao.topCategory(account);
        List<Map<String, Object>> list = new ArrayList<>();
        if (topCategory != null) {
            list = dao.recommendByCategory(topCategory, account, today, RECOMMEND_LIMIT);
        }
        // 无偏好或偏好类别已无可推荐的活动时，兜底推荐最新活动
        if (list.isEmpty()) {
            topCategory = "";
            list = dao.listLatestForRecommend(account, today, RECOMMEND_LIMIT);
        }

        var resp = new LinkedHashMap<String, Object>();
        resp.put("category", topCategory);
        resp.put("activities", list);
        return resp;
    }
}