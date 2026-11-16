package com.campus.config;

import com.campus.util.SecurityUtil;

import java.sql.*;
import java.util.logging.Logger;

/**
 * 数据库Schema管理 - 建表、索引、示例数据
 */
public final class DbSchema {
    private static final Logger LOG = Logger.getLogger(DbSchema.class.getName());

    public DbSchema() {}

    public void initSchema() throws Exception { initTables(); }
    public void initSampleData() throws Exception { initSampleDataStatic(); }

    private static void initTables() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        account    TEXT PRIMARY KEY,
                        password   TEXT NOT NULL,
                        nickname   TEXT NOT NULL,
                        school     TEXT DEFAULT '',
                        phone      TEXT DEFAULT '',
                        vip_level  TEXT DEFAULT 'free',
                        vip_expire TEXT DEFAULT NULL,
                        role       TEXT DEFAULT 'user',
                        banned     INTEGER DEFAULT 0
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS activities (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        name        TEXT NOT NULL,
                        category    TEXT NOT NULL,
                        description TEXT DEFAULT '',
                        date        TEXT NOT NULL,
                        time        TEXT DEFAULT '14:00',
                        location    TEXT NOT NULL,
                        school      TEXT NOT NULL,
                        max_people  INTEGER DEFAULT 0,
                        fee         REAL DEFAULT 0,
                        publisher   TEXT NOT NULL,
                        tags        TEXT DEFAULT '',
                        promoted    INTEGER DEFAULT 0,
                        promote_plan TEXT DEFAULT '',
                        promote_days INTEGER DEFAULT 0,
                        status      INTEGER DEFAULT 1,
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000),
                        FOREIGN KEY (publisher) REFERENCES users(account)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS signups (
                        activity_id INTEGER NOT NULL,
                        account     TEXT NOT NULL,
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000),
                        PRIMARY KEY (activity_id, account)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ratings (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        activity_id INTEGER NOT NULL,
                        account     TEXT NOT NULL,
                        stars       INTEGER NOT NULL CHECK(stars BETWEEN 1 AND 5),
                        comment     TEXT DEFAULT '',
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        account     TEXT NOT NULL,
                        activity_id INTEGER NOT NULL,
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000),
                        PRIMARY KEY (account, activity_id)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS promotions (
                        activity_id INTEGER PRIMARY KEY,
                        plan        TEXT NOT NULL,
                        days        INTEGER NOT NULL,
                        total_cost  REAL NOT NULL,
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        token     TEXT PRIMARY KEY,
                        account   TEXT NOT NULL,
                        created_at INTEGER DEFAULT (strftime('%s','now')*1000)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS comments (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        activity_id INTEGER NOT NULL,
                        account     TEXT NOT NULL,
                        content     TEXT NOT NULL,
                        created_at  INTEGER DEFAULT (strftime('%s','now')*1000)
                    )
                """);
                // 旧库升级：补充新列，列已存在时忽略报错（保证幂等）
                String[] upgrades = {
                    "ALTER TABLE users ADD COLUMN role TEXT DEFAULT 'user'",
                    "ALTER TABLE users ADD COLUMN banned INTEGER DEFAULT 0",
                    "ALTER TABLE activities ADD COLUMN status INTEGER DEFAULT 1"
                };
                for (var sql : upgrades) {
                    try (var ustmt = conn.createStatement()) { ustmt.executeUpdate(sql); }
                    catch (SQLException ignored) {}
                }
                // 索引
                String[] indexes = {
                    "CREATE INDEX IF NOT EXISTS idx_act_category ON activities(category)",
                    "CREATE INDEX IF NOT EXISTS idx_act_publisher ON activities(publisher)",
                    "CREATE INDEX IF NOT EXISTS idx_act_created ON activities(created_at)",
                    "CREATE INDEX IF NOT EXISTS idx_signup_account ON signups(account)",
                    "CREATE INDEX IF NOT EXISTS idx_rating_activity ON ratings(activity_id)",
                    "CREATE INDEX IF NOT EXISTS idx_fav_account ON favorites(account)",
                    "CREATE INDEX IF NOT EXISTS idx_session_account ON sessions(account)",
                    "CREATE INDEX IF NOT EXISTS idx_session_token ON sessions(token)",
                    "CREATE INDEX IF NOT EXISTS idx_comment_activity ON comments(activity_id)"
                };
                for (var idx : indexes) stmt.executeUpdate(idx);
            }
            LOG.info("数据库表结构初始化完成");
        } finally {
            pool.release(conn);
        }
    }

    public static void initSampleDataStatic() throws Exception {
        var pool = DbPool.getInstance();
        var conn = pool.borrow();
        try {
            // 检查是否已有数据
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
                if (rs.next() && rs.getInt(1) > 0) {
                    LOG.info("数据库已有数据，跳过示例数据初始化");
                    return;
                }
            }
            // 示例用户（密码为SHA-256加盐哈希，含平台管理员）
            try (var ps = conn.prepareStatement(
                    "INSERT INTO users(account,password,nickname,school,phone,vip_level,vip_expire,role) VALUES(?,?,?,?,?,?,?,?)")) {
                Object[][] users = {
                    {"demo", SecurityUtil.hash("demo","12345678"), "演示用户", "北京大学", "13800138000", "premium", "2027-12-31", "user"},
                    {"test", SecurityUtil.hash("test","12345678"), "测试同学", "清华大学", "13900139000", "campus", "2027-12-31", "user"},
                    {"admin", SecurityUtil.hash("admin","admin123456"), "平台管理员", "花海平台", "", "free", null, "admin"}
                };
                for (Object[] u : users) {
                    for (int i = 0; i < u.length; i++) ps.setObject(i+1, u[i]);
                    ps.executeUpdate();
                }
            }
            // 示例活动
            try (var ps = conn.prepareStatement(
                    "INSERT INTO activities(name,category,description,date,time,location,school,max_people,fee,publisher,tags,promoted,promote_plan,promote_days) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                Object[][] acts = {
                    {"AI时代的大学生职业规划论坛","lecture","随着AI技术的飞速发展，大学生的职业规划面临前所未有的机遇与挑战。本次论坛邀请到多位行业大咖、资深HR和成功校友，共同探讨AI时代下的职业发展路径。","2026-12-05","14:00","北京大学 百周年纪念讲堂","北京大学",300,29.9,"demo","AI,职业规划,论坛",1,"premium",7},
                    {"全国大学生电竞联赛·春季赛","sports","全国高校电竞爱好者的巅峰对决！覆盖LOL、王者荣耀、CS2等热门项目，线上预选+线下决赛。","2026-12-12","10:00","浙江大学 紫金港体育馆","浙江大学",500,49.9,"demo","电竞,联赛,春季赛",1,"ultimate",7},
                    {"黑客马拉松 48H 创新挑战赛","tech","48小时极限编程挑战！组队开发创新项目，争夺万元奖金。提供导师指导、技术支持和免费餐饮。","2026-12-20","09:00","清华大学 FIT楼","清华大学",200,19.9,"test","黑客马拉松,创新,编程",0,"",0},
                    {"校园音乐节·冬日狂欢夜","art","多校联合音乐盛典！摇滚、民谣、电音多舞台同时开演，还有美食市集和创意工坊。","2027-01-05","18:00","武汉大学 梅操操场","武汉大学",1000,39.9,"demo","音乐节,演出,冬日",0,"",0},
                    {"乡村支教志愿者招募计划","volunteer","寒假乡村支教项目，为偏远地区孩子带去知识与温暖。提供培训、交通补贴和志愿服务证书。","2027-01-10","09:00","复旦大学 邯郸校区","复旦大学",100,0,"test","支教,志愿,公益",0,"",0},
                    {"跨校联谊·读书分享会","social","南京多校联合读书分享活动，以书会友，拓展人脉。每期精选主题，轻松社交。","2026-11-28","14:00","南京大学 仙林校区","南京大学",60,9.9,"test","读书,联谊,社交",0,"",0},
                    {"创业大咖面对面·经验分享","lecture","邀请3位成功创业者分享从0到1的创业历程，涵盖融资、团队、产品等核心话题。","2026-12-08","15:00","上海交大 闵行校区","上海交大",200,15,"demo","创业,分享,经验",0,"",0},
                    {"校园篮球3v3挑战赛","sports","热血篮球，3v3对抗！自由组队或单人报名，争夺校际冠军。提供专业裁判和奖品。","2026-12-15","09:00","中山大学 南校区体育馆","中山大学",128,20,"test","篮球,3v3,体育",0,"",0},
                    {"【已结束】2025春季社团招新回顾展","social","往期活动回顾展示，用于演示过期活动不可报名的平台规则。","2025-06-01","09:00","北京大学 逸夫楼","北京大学",100,0,"demo","往期,回顾",0,"",0},
                    {"【已满员】新生跨校线上见面会","social","新生线上破冰见面会，名额有限先到先得，用于演示人数上限的平台规则。","2026-11-20","19:00","线上 腾讯会议","花海平台",1,0,"demo","新生,见面会",0,"",0}
                };
                for (Object[] a : acts) {
                    for (int i = 0; i < a.length; i++) ps.setObject(i+1, a[i]);
                    ps.executeUpdate();
                }
            }
            // 示例报名、评分、推广、评论
            try (var ps = conn.prepareStatement("INSERT INTO signups(activity_id,account) VALUES(?,?)")) {
                ps.setInt(1,1); ps.setString(2,"test"); ps.executeUpdate();
                ps.setInt(1,2); ps.setString(2,"test"); ps.executeUpdate();
                ps.setInt(1,10); ps.setString(2,"demo"); ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("INSERT INTO ratings(activity_id,account,stars,comment) VALUES(?,?,?,?)")) {
                ps.setInt(1,1); ps.setString(2,"test"); ps.setInt(3,4); ps.setString(4,"内容丰富，受益匪浅"); ps.executeUpdate();
                ps.setInt(1,1); ps.setString(2,"demo"); ps.setInt(3,5); ps.setString(4,"非常棒的活动！"); ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("INSERT INTO comments(activity_id,account,content) VALUES(?,?,?)")) {
                ps.setInt(1,1); ps.setString(2,"demo"); ps.setString(3,"已经报名了，期待和大佬们面对面交流！"); ps.executeUpdate();
                ps.setInt(1,1); ps.setString(2,"test"); ps.setString(3,"求组队一起去，评论区滴滴我~"); ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("INSERT INTO promotions(activity_id,plan,days,total_cost) VALUES(?,?,?,?)")) {
                ps.setInt(1,1); ps.setString(2,"premium"); ps.setInt(3,7); ps.setDouble(4,120*7*0.8); ps.executeUpdate();
                ps.setInt(1,2); ps.setString(2,"ultimate"); ps.setInt(3,7); ps.setDouble(4,200*7*0.8); ps.executeUpdate();
            }
            LOG.info("示例数据初始化完成");
        } finally {
            pool.release(conn);
        }
    }
}
