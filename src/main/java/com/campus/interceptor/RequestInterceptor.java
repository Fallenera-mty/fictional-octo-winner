package com.campus.interceptor;

import com.campus.model.ApiException;
import com.campus.model.ApiResult;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 请求拦截器链 - 统一异常处理、日志、CORS
 */
public final class RequestInterceptor {
    private static final Logger LOG = Logger.getLogger(RequestInterceptor.class.getName());

    private RequestInterceptor() {}

    @FunctionalInterface
    public interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }

    /**
     * 包装处理器：异常处理 + 日志 + CORS
     */
    public static Handler wrap(Handler handler) {
        return exchange -> {
            var start = System.currentTimeMillis();
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            try {
                // CORS预检
                if ("OPTIONS".equals(method)) {
                    var headers = exchange.getResponseHeaders();
                    headers.set("Access-Control-Allow-Origin", "*");
                    headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                    headers.set("Access-Control-Max-Age", "86400");
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                handler.handle(exchange);
            } catch (ApiException e) {
                sendJson(exchange, e.getCode(), ApiResult.fail(e));
            } catch (Exception e) {
                LOG.warning("请求异常 [" + method + " " + path + "]: " + e.getMessage());
                sendJson(exchange, 500, ApiResult.fail(500, "服务器内部错误"));
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > 100) {
                    LOG.fine("慢请求 [" + method + " " + path + "]: " + elapsed + "ms");
                }
            }
        };
    }

    private static void sendJson(HttpExchange exchange, int code, Map<String, Object> data) {
        try {
            var json = com.campus.api.JsonUtil.toJson(data);
            var bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            headers.set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(code, bytes.length);
            try (var os = exchange.getResponseBody()) { os.write(bytes); }
        } catch (IOException e) {
            LOG.warning("响应发送失败: " + e.getMessage());
        }
    }
}
