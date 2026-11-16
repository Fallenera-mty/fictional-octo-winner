package com.campus.dao;

import com.campus.config.DbPool;

import java.util.*;

/**
 * 会话数据访问层
 */
public final class SessionDao {
    private static final SessionDao INSTANCE = new SessionDao();
    private SessionDao() {}
    public static SessionDao getInstance() { return INSTANCE; }

    public void save(String token, String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("INSERT INTO sessions(token,account) VALUES(?,?)")) {
                ps.setString(1, token); ps.setString(2, account); ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public String findAccountByToken(String token) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT account FROM sessions WHERE token=?")) {
                ps.setString(1, token);
                var rs = ps.executeQuery();
                return rs.next() ? rs.getString("account") : null;
            }
        } finally { pool.release(conn); }
    }

    public void deleteByToken(String token) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("DELETE FROM sessions WHERE token=?")) {
                ps.setString(1, token); ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public void cleanExpired(long timeoutMs) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            long threshold = System.currentTimeMillis() - timeoutMs;
            try (var ps = conn.prepareStatement("DELETE FROM sessions WHERE created_at < ?")) {
                ps.setLong(1, threshold); ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }
}
