package com.campus.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码安全工具 - SHA-256加盐哈希
 * 盐采用账号本身，每个用户盐值不同，无需额外存储盐字段
 * 存储格式: sha256:<hex>，带前缀便于识别并兼容旧明文数据平滑迁移
 */
public final class SecurityUtil {
    private static final String PREFIX = "sha256:";

    private SecurityUtil() {}

    public static String hash(String account, String password) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest((account + ":" + password).getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return PREFIX + sb;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /**
     * 校验密码：新格式比对哈希，旧明文数据直接比对（用于登录时平滑迁移）
     */
    public static boolean matches(String account, String rawPassword, String stored) {
        if (stored == null) return false;
        if (isHashed(stored)) return stored.equals(hash(account, rawPassword));
        return stored.equals(rawPassword);
    }
}