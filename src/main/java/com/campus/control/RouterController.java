package com.campus.control;

import com.campus.api.HttpHelper;
import com.campus.api.JsonUtil;
import com.campus.interceptor.AuthInterceptor;
import com.campus.interceptor.RequestInterceptor;
import com.campus.model.ApiResult;
import com.campus.service.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * 路由控制器 - 注册所有API和静态文件路由
 */
public final class RouterController {
    private static final Logger LOG = Logger.getLogger(RouterController.class.getName());

    private RouterController() {}

    public static void register(HttpServer server) {
        server.createContext("/api/register", wrap(RouterController::handleRegister));
        server.createContext("/api/login", wrap(RouterController::handleLogin));
        server.createContext("/api/logout", wrap(RouterController::handleLogout));
        server.createContext("/api/user/update", wrap(RouterController::handleUserUpdate));
        server.createContext("/api/user/activities", wrap(RouterController::handleMyActivities));
        server.createContext("/api/user/signups", wrap(RouterController::handleMySignups));
        server.createContext("/api/user/favorites", wrap(RouterController::handleMyFavorites));
        server.createContext("/api/user", wrap(RouterController::handleUser));
        server.createContext("/api/activities", wrap(RouterController::handleActivities));
        server.createContext("/api/vip/buy", wrap(RouterController::handleVipBuy));
        server.createContext("/api/promote/buy", wrap(RouterController::handlePromoteBuy));
        server.createContext("/api/stats", wrap(RouterController::handleStats));
        server.createContext("/api/admin/", wrap(RouterController::handleAdminRoutes));
        server.createContext("/api/recommend", wrap(RouterController::handleRecommend));
        server.createContext("/api/activities/", wrap(RouterController::handleActivitySubRoutes));
        server.createContext("/", wrap(RouterController::handleStaticFile));
    }

    private static com.sun.net.httpserver.HttpHandler wrap(RequestInterceptor.Handler handler) {
        return exchange -> {
            try { RequestInterceptor.wrap(handler).handle(exchange); }
            catch (Exception e) { throw new RuntimeException(e); }
        };
    }

    // ==================== 用户 ====================

    private static void handleRegister(HttpExchange e) throws Exception {
        if (!"POST".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
        var p = JsonUtil.parse(HttpHelper.readBody(e));
        var user = UserService.getInstance().register(
            (String)p.getOrDefault("account",""), (String)p.getOrDefault("password",""),
            (String)p.getOrDefault("nickname",""), (String)p.getOrDefault("school",""),
            (String)p.getOrDefault("phone",""));
        var resp = new LinkedHashMap<String, Object>(); resp.put("user", user.toMap());
        HttpHelper.sendSuccess(e, resp);
    }

    private static void handleLogin(HttpExchange e) throws Exception {
        if (!"POST".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
        var p = JsonUtil.parse(HttpHelper.readBody(e));
        var result = UserService.getInstance().login(
            (String)p.getOrDefault("account",""), (String)p.getOrDefault("password",""));
        HttpHelper.sendSuccess(e, result);
    }

    private static void handleLogout(HttpExchange e) throws Exception {
        var auth = e.getRequestHeaders().getFirst("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
        UserService.getInstance().logout(token);
        HttpHelper.sendSuccess(e, new LinkedHashMap<>());
    }

    private static void handleUser(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        var user = UserService.getInstance().getUser(account);
        var resp = new LinkedHashMap<String, Object>(); resp.put("user", user.toMap());
        HttpHelper.sendSuccess(e, resp);
    }

    private static void handleUserUpdate(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        var p = JsonUtil.parse(HttpHelper.readBody(e));
        var user = UserService.getInstance().updateProfile(account,
            (String)p.get("nickname"), (String)p.get("school"), (String)p.get("phone"),
            (String)p.getOrDefault("oldPassword",""), (String)p.get("newPassword"));
        var resp = new LinkedHashMap<String, Object>(); resp.put("user", user.toMap());
        HttpHelper.sendSuccess(e, resp);
    }

    private static void handleMyActivities(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().getMyActivities(account));
    }

    private static void handleMySignups(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().getMySignups(account));
    }

    private static void handleMyFavorites(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().getMyFavorites(account));
    }

    // ==================== 活动 ====================

    private static void handleActivities(HttpExchange e) throws Exception {
        if ("GET".equals(e.getRequestMethod())) {
            String keyword = "", category = "";
            var query = e.getRequestURI().getQuery();
            if (query != null) {
                for (var param : query.split("&")) {
                    var kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("keyword".equals(kv[0])) keyword = URLDecoder.decode(kv[1], "UTF-8");
                        if ("category".equals(kv[0])) category = URLDecoder.decode(kv[1], "UTF-8");
                    }
                }
            }
            HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().listActivities(keyword, category));
        } else if ("POST".equals(e.getRequestMethod())) {
            var account = AuthInterceptor.requireLogin(e);
            var user = UserService.getInstance().getUser(account);
            if ("free".equals(user.vipLevel)) throw com.campus.model.ApiException.forbidden("普通用户无法发布活动，请先开通会员");
            var p = JsonUtil.parse(HttpHelper.readBody(e));
            var actMap = ActivityService.getInstance().createActivity(account,
                (String)p.getOrDefault("name",""), (String)p.getOrDefault("category",""),
                (String)p.getOrDefault("description",""), (String)p.getOrDefault("date",""),
                (String)p.getOrDefault("time","14:00"), (String)p.getOrDefault("location",""),
                (String)p.getOrDefault("school",""),
                p.containsKey("maxPeople") ? ((Number)p.get("maxPeople")).intValue() : 0,
                p.containsKey("fee") ? ((Number)p.get("fee")).doubleValue() : 0,
                (String)p.getOrDefault("tags",""),
                p.containsKey("promoted") && (boolean)p.getOrDefault("promoted",false),
                (String)p.getOrDefault("promotePlan",""),
                p.containsKey("promoteDays") ? ((Number)p.get("promoteDays")).intValue() : 0);
            var resp = new LinkedHashMap<String, Object>(); resp.put("activity", actMap);
            HttpHelper.sendSuccess(e, resp);
        } else {
            throw com.campus.model.ApiException.badRequest("Method Not Allowed");
        }
    }

    private static void handleActivitySubRoutes(HttpExchange e) throws Exception {
        var path = e.getRequestURI().getPath();
        var parts = path.split("/");
        if (parts.length < 4) throw com.campus.model.ApiException.badRequest("Invalid path");
        int id;
        try { id = Integer.parseInt(parts[3]); }
        catch (NumberFormatException ex) { throw com.campus.model.ApiException.badRequest("Invalid activity ID"); }
        String action = parts.length >= 5 ? parts[4] : "";

        switch (action) {
            case "signup" -> {
                var account = AuthInterceptor.requireLogin(e);
                var count = ActivityService.getInstance().signup(account, id);
                var resp = new LinkedHashMap<String, Object>(); resp.put("signupCount", count);
                HttpHelper.sendSuccess(e, resp);
            }
            case "cancel" -> {
                var account = AuthInterceptor.requireLogin(e);
                var count = ActivityService.getInstance().cancelSignup(account, id);
                var resp = new LinkedHashMap<String, Object>(); resp.put("signupCount", count);
                HttpHelper.sendSuccess(e, resp);
            }
            case "rate" -> {
                var account = AuthInterceptor.requireLogin(e);
                var p = JsonUtil.parse(HttpHelper.readBody(e));
                int stars = p.containsKey("stars") ? ((Number)p.get("stars")).intValue() : 0;
                var result = ActivityService.getInstance().rate(account, id, stars, (String)p.getOrDefault("comment",""));
                HttpHelper.sendSuccess(e, result);
            }
            case "favorite" -> {
                var account = AuthInterceptor.requireLogin(e);
                boolean favorited = ActivityService.getInstance().toggleFavorite(account, id);
                var resp = new LinkedHashMap<String, Object>(); resp.put("favorited", favorited);
                HttpHelper.sendSuccess(e, resp);
            }
            case "delete" -> {
                var account = AuthInterceptor.requireLogin(e);
                ActivityService.getInstance().deleteActivity(account, id);
                var resp = new LinkedHashMap<String, Object>(); resp.put("deleted", true);
                HttpHelper.sendSuccess(e, resp);
            }
            case "comments" -> {
                if ("POST".equals(e.getRequestMethod())) {
                    var account = AuthInterceptor.requireLogin(e);
                    var p = JsonUtil.parse(HttpHelper.readBody(e));
                    var list = ActivityService.getInstance().addComment(account, id, (String)p.getOrDefault("content",""));
                    HttpHelper.sendJsonList(e, 200, list);
                } else {
                    HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().listComments(id));
                }
            }
            default -> {
                var actMap = ActivityService.getInstance().getActivityDetail(id);
                var resp = new LinkedHashMap<String, Object>(); resp.put("activity", actMap);
                HttpHelper.sendSuccess(e, resp);
            }
        }
    }

    // ==================== 会员/推广 ====================

    private static void handleVipBuy(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        var p = JsonUtil.parse(HttpHelper.readBody(e));
        UserService.getInstance().buyVip(account, (String)p.getOrDefault("plan",""));
        var user = UserService.getInstance().getUser(account);
        var resp = new LinkedHashMap<String, Object>(); resp.put("user", user.toMap());
        HttpHelper.sendSuccess(e, resp);
    }

    private static void handlePromoteBuy(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        var p = JsonUtil.parse(HttpHelper.readBody(e));
        int activityId = p.containsKey("activityId") ? ((Number)p.get("activityId")).intValue() : 0;
        String plan = (String)p.getOrDefault("plan","");
        int days = p.containsKey("days") ? ((Number)p.get("days")).intValue() : 0;
        var result = ActivityService.getInstance().buyPromotion(account, activityId, plan, days);
        HttpHelper.sendSuccess(e, result);
    }

    // ==================== 统计 ====================

    private static void handleStats(HttpExchange e) throws Exception {
        HttpHelper.sendJson(e, 200, StatsService.getInstance().getStats());
    }

    // ==================== 管理员 ====================

    private static void handleAdminRoutes(HttpExchange e) throws Exception {
        AuthInterceptor.requireAdmin(e);
        var path = e.getRequestURI().getPath();
        var parts = path.split("/");
        String resource = parts.length >= 4 ? parts[3] : "";

        switch (resource) {
            case "activities" -> {
                if (parts.length == 4) {
                    if (!"GET".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
                    HttpHelper.sendJsonList(e, 200, ActivityService.getInstance().listAllForAdmin());
                } else if (parts.length == 6) {
                    if (!"POST".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
                    int id;
                    try { id = Integer.parseInt(parts[4]); }
                    catch (NumberFormatException ex) { throw com.campus.model.ApiException.badRequest("Invalid activity ID"); }
                    switch (parts[5]) {
                        case "status" -> {
                            var p = JsonUtil.parse(HttpHelper.readBody(e));
                            int status = p.containsKey("status") ? ((Number)p.get("status")).intValue() : -1;
                            ActivityService.getInstance().updateActivityStatus(id, status);
                            var resp = new LinkedHashMap<String, Object>(); resp.put("updated", true);
                            HttpHelper.sendSuccess(e, resp);
                        }
                        case "delete" -> {
                            ActivityService.getInstance().deleteActivityByAdmin(id);
                            var resp = new LinkedHashMap<String, Object>(); resp.put("deleted", true);
                            HttpHelper.sendSuccess(e, resp);
                        }
                        default -> throw com.campus.model.ApiException.badRequest("Invalid action");
                    }
                } else {
                    throw com.campus.model.ApiException.badRequest("Invalid path");
                }
            }
            case "users" -> {
                if (parts.length == 4) {
                    if (!"GET".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
                    var list = new ArrayList<Map<String, Object>>();
                    for (var u : UserService.getInstance().listUsers()) list.add(u.toMap());
                    HttpHelper.sendJsonList(e, 200, list);
                } else if (parts.length == 6) {
                    if (!"POST".equals(e.getRequestMethod())) throw com.campus.model.ApiException.badRequest("Method Not Allowed");
                    String target = URLDecoder.decode(parts[4], "UTF-8");
                    switch (parts[5]) {
                        case "ban" -> UserService.getInstance().banUser(target);
                        case "unban" -> UserService.getInstance().unbanUser(target);
                        default -> throw com.campus.model.ApiException.badRequest("Invalid action");
                    }
                    HttpHelper.sendSuccess(e, new LinkedHashMap<>());
                } else {
                    throw com.campus.model.ApiException.badRequest("Invalid path");
                }
            }
            default -> throw com.campus.model.ApiException.notFound("Not Found");
        }
    }

    // ==================== 智能推荐 ====================

    private static void handleRecommend(HttpExchange e) throws Exception {
        var account = AuthInterceptor.requireLogin(e);
        HttpHelper.sendSuccess(e, RecommendService.getInstance().recommend(account));
    }

    // ==================== 静态文件 ====================

    private static void handleStaticFile(HttpExchange e) throws Exception {
        var config = com.campus.config.AppConfig.getInstance();
        String path = e.getRequestURI().getPath();
        if ("/".equals(path)) path = "/html/login.html";

        Path filePath = Paths.get(config.getWebRoot(), path).toAbsolutePath().normalize();
        Path rootPath = Paths.get(config.getWebRoot()).toAbsolutePath().normalize();

        if (!filePath.startsWith(rootPath)) { HttpHelper.sendError(e, 403, "Forbidden"); return; }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) { HttpHelper.sendError(e, 404, "Not Found"); return; }

        String contentType = "application/octet-stream";
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(".html")) contentType = "text/html; charset=utf-8";
        else if (fileName.endsWith(".css")) contentType = "text/css; charset=utf-8";
        else if (fileName.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
        else if (fileName.endsWith(".json")) contentType = "application/json; charset=utf-8";
        else if (fileName.endsWith(".png")) contentType = "image/png";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (fileName.endsWith(".svg")) contentType = "image/svg+xml";

        byte[] content = Files.readAllBytes(filePath);
        e.getResponseHeaders().set("Content-Type", contentType);
        e.getResponseHeaders().set("Cache-Control", "no-cache");
        e.sendResponseHeaders(200, content.length);
        try (var os = e.getResponseBody()) { os.write(content); }
    }
}
