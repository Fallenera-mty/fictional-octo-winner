package com.campus.dao;

import com.campus.config.DbPool;
import com.campus.dataobject.UserDO;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 用户数据访问层
 */
public final class UserDao {
    private static final UserDao INSTANCE = new UserDao();
    private UserDao() {}
    public static UserDao getInstance() { return INSTANCE; }

    public UserDO findByAccount(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT * FROM users WHERE account=?")) {
                ps.setString(1, account);
                var rs = ps.executeQuery();
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        } finally { pool.release(conn); }
    }

    public boolean exists(String account) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT 1 FROM users WHERE account=?")) {
                ps.setString(1, account);
                return ps.executeQuery().next();
            }
        } finally { pool.release(conn); }
    }

    public void insert(UserDO user) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO users(account,password,nickname,school,phone) VALUES(?,?,?,?,?)")) {
                ps.setString(1, user.account); ps.setString(2, user.password);
                ps.setString(3, user.nickname); ps.setString(4, user.school);
                ps.setString(5, user.phone);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public void updateVip(String account, String plan) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var expire = LocalDateTime.now().plusMonths(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            try (var ps = conn.prepareStatement("UPDATE users SET vip_level=?, vip_expire=? WHERE account=?")) {
                ps.setString(1, plan); ps.setString(2, expire); ps.setString(3, account);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public void updateProfile(String account, String nickname, String school, String phone,
                               String oldPwd, String newPwd) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var updates = new java.util.ArrayList<String>();
            var params = new java.util.ArrayList<Object>();
            if (nickname != null) { updates.add("nickname=?"); params.add(nickname); }
            if (school != null) { updates.add("school=?"); params.add(school); }
            if (phone != null) { updates.add("phone=?"); params.add(phone); }
            if (newPwd != null) { updates.add("password=?"); params.add(newPwd); }
            if (updates.isEmpty()) return;
            var sql = "UPDATE users SET " + String.join(",", updates) + " WHERE account=?";
            try (var ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
                ps.setString(params.size()+1, account);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public int countByVipLevel(String level) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE vip_level=?")) {
                ps.setString(1, level);
                return ps.executeQuery().getInt(1);
            }
        } finally { pool.release(conn); }
    }

    public int countAll() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var stmt = conn.createStatement()) {
                return stmt.executeQuery("SELECT COUNT(*) FROM users").getInt(1);
            }
        } finally { pool.release(conn); }
    }

    public List<UserDO> listAll() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            var list = new ArrayList<UserDO>();
            try (var ps = conn.prepareStatement("SELECT * FROM users ORDER BY account")) {
                var rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        } finally { pool.release(conn); }
    }

    public void updateBanned(String account, boolean banned) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("UPDATE users SET banned=? WHERE account=?")) {
                ps.setInt(1, banned ? 1 : 0); ps.setString(2, account);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    public void updatePassword(String account, String newHash) throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var ps = conn.prepareStatement("UPDATE users SET password=? WHERE account=?")) {
                ps.setString(1, newHash); ps.setString(2, account);
                ps.executeUpdate();
            }
        } finally { pool.release(conn); }
    }

    private UserDO mapRow(ResultSet rs) throws SQLException {
        var u = new UserDO();
        u.account = rs.getString("account");
        u.password = rs.getString("password");
        u.nickname = rs.getString("nickname");
        u.school = rs.getString("school");
        u.phone = rs.getString("phone");
        u.vipLevel = rs.getString("vip_level");
        u.vipExpire = rs.getString("vip_expire");
        u.role = rs.getString("role");
        u.banned = rs.getInt("banned") == 1;
        return u;
    }
}
