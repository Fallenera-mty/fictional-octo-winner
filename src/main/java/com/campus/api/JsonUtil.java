package com.campus.api;

import java.util.*;

/**
 * JSON工具 - 解析与序列化
 */
public final class JsonUtil {
    private JsonUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String body) {
        var result = new LinkedHashMap<String, Object>();
        if (body == null || body.trim().isEmpty()) return result;
        body = body.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) return result;
        body = body.substring(1, body.length() - 1).trim();
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && body.charAt(i) != '"') i++;
            if (i >= body.length()) break;
            i++;
            int keyStart = i;
            while (i < body.length() && body.charAt(i) != '"') i++;
            String key = body.substring(keyStart, i);
            i++;
            while (i < body.length() && (body.charAt(i) == ':' || body.charAt(i) == ' ')) i++;
            if (i < body.length() && body.charAt(i) == '"') {
                i++;
                int valStart = i;
                while (i < body.length() && body.charAt(i) != '"') i++;
                result.put(key, body.substring(valStart, i));
                i++;
            } else if (i < body.length() && (Character.isDigit(body.charAt(i)) || body.charAt(i) == '-')) {
                int valStart = i;
                if (body.charAt(i) == '-') i++;
                while (i < body.length() && (Character.isDigit(body.charAt(i)) || body.charAt(i) == '.')) i++;
                String numStr = body.substring(valStart, i);
                result.put(key, numStr.contains(".") ? Double.parseDouble(numStr) : Long.parseLong(numStr));
            } else if (i < body.length() && body.charAt(i) == 't') { result.put(key, true); i += 4;
            } else if (i < body.length() && body.charAt(i) == 'f') { result.put(key, false); i += 5;
            } else if (i < body.length() && body.charAt(i) == 'n') { result.put(key, null); i += 4; }
            while (i < body.length() && (body.charAt(i) == ',' || body.charAt(i) == ' ')) i++;
        }
        return result;
    }

    public static String toJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":").append(val(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonList(List<?> list) {
        var sb = new StringBuilder("[");
        boolean first = true;
        for (var item : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(val(item));
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String val(Object v) {
        if (v == null) return "null";
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Map) return toJson((Map<String, Object>) v);
        if (v instanceof List) return toJsonList((List<?>) v);
        return "\"" + escape(String.valueOf(v)) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
