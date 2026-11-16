package com.campus.interceptor;

import com.campus.dao.SessionDao;
import com.campus.dao.UserDao;
import com.campus.model.ApiException;
import com.sun.net.httpserver.HttpExchange;

/**
 * 认证拦截器 - 统一鉴权逻辑
 */
public final class AuthInterceptor {
    private AuthInterceptor() {}

    /**
     * 从请求中提取已认证的用户account，未认证则抛异常
     */
    public static String requireLogin(HttpExchange exchange) throws Exception {
        var account = getLoginAccount(exchange);
        if (account == null) throw ApiException.unauthorized("未登录");
        return account;
    }

    /**
     * 要求管理员权限，非管理员抛403
     */
    public static String requireAdmin(HttpExchange exchange) throws Exception {
        String account = requireLogin(exchange);
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null || !"admin".equals(user.role))
            throw ApiException.forbidden("需要管理员权限");
        return account;
    }

    /**
     * 从请求中提取用户account，未认证返回null
     */
    public static String getLoginAccount(HttpExchange exchange) throws Exception {
        // 1. Authorization头
        var auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            var account = SessionDao.getInstance().findAccountByToken(token);
            if (account != null) return account;
        }
        // 2. Query参数
        var query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (var param : query.split("&")) {
                var kv = param.split("=", 2);
                if ("token".equals(kv[0]) && kv.length == 2) {
                    return SessionDao.getInstance().findAccountByToken(kv[1]);
                }
            }
        }
        return null;
    }
}
