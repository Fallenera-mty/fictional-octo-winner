package com.campus.model;

/**
 * 业务异常 - 统一错误码体系
 */
public class ApiException extends Exception {
    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }

    // 常用工厂方法
    public static ApiException badRequest(String msg) { return new ApiException(400, msg); }
    public static ApiException unauthorized(String msg) { return new ApiException(401, msg); }
    public static ApiException forbidden(String msg) { return new ApiException(403, msg); }
    public static ApiException notFound(String msg) { return new ApiException(404, msg); }
    public static ApiException conflict(String msg) { return new ApiException(409, msg); }
}
