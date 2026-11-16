package com.campus.dataobject;

import java.util.*;

/**
 * 用户数据对象 - 与数据库users表对应
 */
public class UserDO {
    public String account;
    public String password;
    public String nickname;
    public String school;
    public String phone;
    public String vipLevel;
    public String vipExpire;
    public String role;
    public boolean banned;

    public Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("account", account);
        m.put("nickname", nickname);
        m.put("school", school);
        m.put("phone", phone);
        m.put("vipLevel", vipLevel);
        m.put("vipExpire", vipExpire);
        m.put("role", role);
        m.put("banned", banned);
        return m;
    }
}
