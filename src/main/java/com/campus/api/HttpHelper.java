package com.campus.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP请求/响应工具
 */
public final class HttpHelper {
    private HttpHelper() {}

    public static String readBody(HttpExchange exchange) throws IOException {
        try (var is = exchange.getRequestBody();
             var baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
            return baos.toString("UTF-8");
        }
    }

    public static void sendJson(HttpExchange exchange, int code, Map<String, Object> data) throws IOException {
        byte[] bytes = JsonUtil.toJson(data).getBytes(StandardCharsets.UTF_8);
        var h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public static void sendJsonList(HttpExchange exchange, int code, List<Map<String, Object>> list) throws IOException {
        byte[] bytes = JsonUtil.toJsonList(list).getBytes(StandardCharsets.UTF_8);
        var h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public static void sendSuccess(HttpExchange exchange, Map<String, Object> data) throws IOException {
        data.put("success", true);
        sendJson(exchange, 200, data);
    }

    public static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        var resp = new LinkedHashMap<String, Object>();
        resp.put("success", false);
        resp.put("message", message);
        sendJson(exchange, code, resp);
    }
}
