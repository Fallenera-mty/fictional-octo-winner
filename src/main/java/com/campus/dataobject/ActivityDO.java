package com.campus.dataobject;

import java.util.*;

/**
 * 活动数据对象 - 与数据库activities表对应
 */
public class ActivityDO {
    public int id;
    public String name, category, description, date, time, endTime, location, school;
    public int maxPeople;
    public double fee;
    public int creditHours;
    public String publisher, tags;
    public boolean promoted;
    public String promotePlan;
    public int promoteDays;
    public int status;
    public long createdAt;

    public Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("description", description);
        m.put("date", date);
        m.put("time", time);
        m.put("endTime", endTime);
        m.put("location", location);
        m.put("school", school);
        m.put("maxPeople", maxPeople);
        m.put("fee", fee);
        m.put("creditHours", creditHours);
        m.put("publisher", publisher);
        m.put("tags", tags);
        m.put("promoted", promoted);
        m.put("promotePlan", promotePlan);
        m.put("promoteDays", promoteDays);
        m.put("status", status);
        m.put("createdAt", createdAt);
        return m;
    }
}
