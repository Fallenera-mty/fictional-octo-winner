package com.campus.dataobject;

import java.util.*;

/**
 * 评分数据对象
 */
public class RatingDO {
    public String account;
    public int stars;
    public String comment;

    public Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("account", account);
        m.put("stars", stars);
        m.put("comment", comment);
        return m;
    }
}
