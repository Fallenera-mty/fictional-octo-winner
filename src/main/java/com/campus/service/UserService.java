package com.campus.service;

import com.campus.dao.*;
import com.campus.dataobject.UserDO;
import com.campus.model.ApiException;
import com.campus.util.SecurityUtil;

import java.util.*;

/**
 * 用户业务逻辑层
 */
public final class UserService {
    private static final UserService INSTANCE = new UserService();
    private UserService() {}
    public static UserService getInstance() { return INSTANCE; }

    public UserDO register(String account, String password, String nickname, String school, String phone,
                           String userType, String teacherNo, String college) throws Exception {
        if (account.isEmpty() || password.isEmpty())
            throw ApiException.badRequest("账号和密码不能为空");
        boolean isTeacher = "teacher".equals(userType);
        if (!isTeacher && (nickname == null || nickname.isEmpty()))
            throw ApiException.badRequest("昵称不能为空");
        if (isTeacher && (teacherNo == null || teacherNo.isBlank() || college == null || college.isBlank()))
            throw ApiException.badRequest("老师注册需填写工号和所在学院");
        if (UserDao.getInstance().exists(account))
            throw ApiException.conflict("该账号已注册");
        var user = new UserDO();
        user.account = account; user.password = SecurityUtil.hash(account, password);
        // 老师身份隐藏昵称显示，后端自动用工号代填，避免出现"未命名老师"
        user.nickname = isTeacher ? (teacherNo.isBlank() ? account : teacherNo.trim()) : nickname;
        user.school = school; user.phone = phone; user.vipLevel = "free";
        user.userType = isTeacher ? "teacher" : "student";
        user.teacherNo = isTeacher ? teacherNo.trim() : "";
        user.college = isTeacher ? college.trim() : "";
        UserDao.getInstance().insert(user);
        return UserDao.getInstance().findByAccount(account);
    }

    public Map<String, Object> login(String account, String password) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null || !SecurityUtil.matches(account, password, user.password))
            throw ApiException.unauthorized("账号或密码错误");
        if (user.banned)
            throw ApiException.forbidden("账号已被封禁，请联系平台管理员");
        // 旧明文密码平滑迁移为哈希存储
        if (!SecurityUtil.isHashed(user.password))
            UserDao.getInstance().updatePassword(account, SecurityUtil.hash(account, password));
        var token = UUID.randomUUID().toString().replace("-", "");
        SessionDao.getInstance().save(token, account);
        var resp = new LinkedHashMap<String, Object>();
        resp.put("token", token);
        resp.put("user", user.toMap());
        return resp;
    }

    public void logout(String token) throws Exception {
        if (token != null) SessionDao.getInstance().deleteByToken(token);
    }

    public UserDO getUser(String account) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null) throw ApiException.notFound("用户不存在");
        return user;
    }

    public UserDO updateProfile(String account, String nickname, String school, String phone,
                                 String oldPwd, String newPwd) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null) throw ApiException.notFound("用户不存在");
        if (newPwd != null && !SecurityUtil.matches(account, oldPwd, user.password))
            throw ApiException.badRequest("旧密码不正确");
        String pwdToStore = (newPwd != null) ? SecurityUtil.hash(account, newPwd) : null;
        UserDao.getInstance().updateProfile(account, nickname, school, phone, oldPwd, pwdToStore);
        return UserDao.getInstance().findByAccount(account);
    }

    public void buyVip(String account, String plan) throws Exception {
        if (!plan.equals("campus") && !plan.equals("premium"))
            throw ApiException.badRequest("无效的会员方案");
        UserDao.getInstance().updateVip(account, plan);
    }

    public double getCommissionRate(String account) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null) return 0.10;
        return "premium".equals(user.vipLevel) ? 0.05 : 0.10;
    }

    public double getPromoteDiscount(String account) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        return (user != null && "premium".equals(user.vipLevel)) ? 0.8 : 1.0;
    }

    // ==================== 管理员操作 ====================

    public List<UserDO> listUsers() throws Exception {
        return UserDao.getInstance().listAll();
    }

    public void banUser(String account) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null) throw ApiException.notFound("用户不存在");
        if ("admin".equals(user.role)) throw ApiException.forbidden("不能封禁管理员账号");
        UserDao.getInstance().updateBanned(account, true);
    }

    public void unbanUser(String account) throws Exception {
        var user = UserDao.getInstance().findByAccount(account);
        if (user == null) throw ApiException.notFound("用户不存在");
        UserDao.getInstance().updateBanned(account, false);
    }
}
