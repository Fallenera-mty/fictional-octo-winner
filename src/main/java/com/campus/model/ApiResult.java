package com.campus.model;

import java.util.*;

/**
 * 统一API响应封装
 */
public final class ApiResult {
    private ApiResult() {}

    public static Map<String, Object> fail(int code, String message) {
        var m = new LinkedHashMap<String, Object>();
        m.put("success", false);
        m.put("code", code);
        m.put("message", message);
        return m;
    }

    public static Map<String, Object> fail(ApiException e) {
        return fail(e.getCode(), e.getMessage());
    }
}
