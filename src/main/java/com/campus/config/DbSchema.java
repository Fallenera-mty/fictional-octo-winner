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
                        user_type  TEXT DEFAULT 'student',
                        teacher_no TEXT DEFAULT '',
                        college    TEXT DEFAULT '',
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
                        end_time    TEXT DEFAULT '',
                        location    TEXT NOT NULL,
                        school      TEXT NOT NULL,
                        max_people  INTEGER DEFAULT 0,
                        fee         REAL DEFAULT 0,
                        credit_hours INTEGER DEFAULT 0,
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
                        activity_id   INTEGER NOT NULL,
                        account       TEXT NOT NULL,
                        created_at    INTEGER DEFAULT (strftime('%s','now')*1000),
                        checked_in_at INTEGER DEFAULT NULL,
                        status        TEXT DEFAULT 'confirmed',
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
                    CREATE TABLE IF NOT EXISTS calendar_items (
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
                    "ALTER TABLE activities ADD COLUMN status INTEGER DEFAULT 1",
                    "ALTER TABLE activities ADD COLUMN credit_hours INTEGER DEFAULT 0",
                    "ALTER TABLE activities ADD COLUMN end_time TEXT DEFAULT ''",
                    "ALTER TABLE users ADD COLUMN user_type TEXT DEFAULT 'student'",
                    "ALTER TABLE users ADD COLUMN teacher_no TEXT DEFAULT ''",
                    "ALTER TABLE users ADD COLUMN college TEXT DEFAULT ''",
                    "ALTER TABLE signups ADD COLUMN checked_in_at INTEGER DEFAULT NULL",
                    "ALTER TABLE signups ADD COLUMN status TEXT DEFAULT 'confirmed'"
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
                migrateLegacyContent(conn);
                backfillCreditHours(conn);
                backfillEndTime(conn);
                // 演示账号保持老师身份，便于体验发布活动
                try (var demoStmt = conn.createStatement()) {
                    demoStmt.executeUpdate("UPDATE users SET user_type='teacher', "
                            + "teacher_no=CASE WHEN teacher_no='' OR teacher_no IS NULL THEN 'T2019001' ELSE teacher_no END, "
                            + "college=CASE WHEN college='' OR college IS NULL THEN '电子与信息工程学院' ELSE college END "
                            + "WHERE account='demo'");
                }
            }
            LOG.info("数据库表结构初始化完成");
        } finally {
            pool.release(conn);
        }
    }

    /** 苏州科技大学校内免费示例活动 */
    private static Object[][] sampleActivities() {
        return new Object[][]{
            {"石湖讲坛：AI 时代的大学生职业规划","lecture","面向苏科大全体学生，邀请校友导师与校就业中心老师，围绕 AI 时代的职业选择、实习准备和简历面试展开分享，现场可一对一咨询。","2026-12-05","14:00","石湖校区 C6-301","苏州科技大学",300,0,"demo","AI,职业规划,讲座",1,"",0,2},
            {"苏科大新生杯 3v3 篮球赛","sports","各学院组队参赛，三人一队，可跨专业组队。比赛在石湖校区体育馆进行，提供裁判、饮用水与奖品，欢迎到场观赛。","2026-12-12","10:00","石湖校区 体育馆","苏州科技大学",500,0,"demo","篮球,新生杯,体育",1,"",0,1},
            {"苏科大 48H 黑客马拉松","tech","48 小时组队开发，主题为智慧校园。提供机房、网络、导师答疑和夜宵，优秀作品将在校创新创业中心孵化。","2026-12-20","09:00","石湖校区 C6-501 机房","苏州科技大学",200,0,"test","编程,创新,黑客马拉松",0,"",0,4},
            {"石湖冬日校园音乐节","art","校乐队、社团与特邀嘉宾轮番演出，现场设创意市集和热饮区，凭学生证免费入场。","2027-01-05","18:00","石湖校区 师陶园","苏州科技大学",1000,0,"demo","音乐节,社团,演出",0,"",0,2},
            {"寒假乡村支教志愿者招募","volunteer","面向全校招募支教志愿者，出发前统一培训，提供交通补贴与志愿服务证明，可认定第二课堂学分。","2027-01-10","09:00","石湖校区 学生活动中心","苏州科技大学",100,0,"test","支教,志愿,公益",0,"",0,3},
            {"石湖读书会 · 以书会友","social","每月一期，本期共读《人类简史》。现场自由分享读书笔记，结束后可结伴去图书馆自习。","2026-11-28","14:00","石湖校区 图书馆报告厅","苏州科技大学",60,0,"test","读书,分享,社交",0,"",0,1},
            {"校友创业经验分享会","lecture","邀请三位苏科大校友回校分享创业经历，覆盖项目起步、团队搭建与校园资源利用，设有自由提问环节。","2026-12-08","15:00","石湖校区 C6-201","苏州科技大学",200,0,"demo","创业,校友,分享",0,"",0,2},
            {"校园荧光夜跑","sports","沿石湖校区环校路线夜跑 5 公里，领取荧光手环和补给，完赛可获纪念徽章，适合零基础参与。","2026-12-15","19:00","石湖校区 田径场","苏州科技大学",128,0,"test","夜跑,体育,荧光",0,"",0,1},
            {"【已结束】2025 春季社团招新回顾展","social","往期社团招新回顾展示，用于演示过期活动不可报名的平台规则。","2025-06-01","09:00","石湖校区 学生活动中心","苏州科技大学",100,0,"demo","往期,社团,回顾",0,"",0,0},
            {"【已满员】石湖夜跑体验场","social","小规模夜跑体验活动，名额有限先到先得，用于演示人数上限与候补规则。","2026-11-20","19:00","石湖校区 田径场","苏州科技大学",1,0,"demo","夜跑,体验,候补",0,"",0,1}
        };
    }

    /** 旧版跨校示例数据的学校标识 */
    private static final String[] LEGACY_SCHOOLS = {
        "北京大学", "清华大学", "浙江大学", "武汉大学", "复旦大学",
        "南京大学", "上海交大", "中山大学", "花海平台"
    };

    /** 把旧库中的跨校示例活动、用户学校原地替换为苏州科技大学，仅执行一次 */
    private static void migrateLegacyContent(Connection conn) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(LEGACY_SCHOOLS.length, "?"));
        boolean legacy;
        try (var ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM activities WHERE school IN (" + placeholders + ")")) {
            for (int i = 0; i < LEGACY_SCHOOLS.length; i++) ps.setString(i + 1, LEGACY_SCHOOLS[i]);
            try (var rs = ps.executeQuery()) {
                legacy = rs.next() && rs.getInt(1) > 0;
            }
        }
        if (!legacy) return;

        try (var ps = conn.prepareStatement(
                "UPDATE users SET school='苏州科技大学' WHERE school IN (" + placeholders + ")")) {
            for (int i = 0; i < LEGACY_SCHOOLS.length; i++) ps.setString(i + 1, LEGACY_SCHOOLS[i]);
            ps.executeUpdate();
        }

        Object[][] acts = sampleActivities();
        String updateSql = "UPDATE activities SET name=?,category=?,description=?,date=?,time=?,"
                + "location=?,school=?,max_people=?,fee=?,publisher=?,tags=?,promoted=?,"
                + "promote_plan=?,promote_days=?,credit_hours=? WHERE id=? AND school IN (" + placeholders + ")";
        try (var ps = conn.prepareStatement(updateSql)) {
            for (int r = 0; r < acts.length; r++) {
                Object[] a = acts[r];
                for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
                ps.setInt(16, r + 1);
                for (int i = 0; i < LEGACY_SCHOOLS.length; i++) ps.setString(17 + i, LEGACY_SCHOOLS[i]);
                ps.executeUpdate();
            }
        }
        LOG.info("已将示例数据替换为苏州科技大学免费活动");
    }

    /** 为已有示例活动补全学时奖励（历史上学时列默认为 0 时执行一次） */
    private static void backfillCreditHours(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM activities WHERE credit_hours > 0")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        Object[][] acts = sampleActivities();
        try (var ps = conn.prepareStatement("UPDATE activities SET credit_hours=? WHERE id=?")) {
            for (int r = 0; r < acts.length; r++) {
                ps.setObject(1, acts[r][14]);
                ps.setInt(2, r + 1);
                ps.executeUpdate();
            }
        }
    }

    /** 为历史活动补全结束时间：默认开始时间 +2 小时，跨天则截到 23:59 */
    private static void backfillEndTime(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(
                "UPDATE activities SET end_time = CASE "
              + "WHEN strftime('%H:%M', datetime(date || ' ' || time, '+2 hours')) <= time THEN '23:59' "
              + "ELSE strftime('%H:%M', datetime(date || ' ' || time, '+2 hours')) END "
              + "WHERE end_time IS NULL OR end_time = ''")) {
            ps.executeUpdate();
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
                    "INSERT INTO users(account,password,nickname,school,phone,vip_level,vip_expire,role,user_type,teacher_no,college) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                Object[][] users = {
                    {"demo", SecurityUtil.hash("demo","12345678"), "演示老师", "苏州科技大学", "13800138000", "free", null, "user", "teacher", "T2019001", "电子与信息工程学院"},
                    {"test", SecurityUtil.hash("test","12345678"), "测试同学", "苏州科技大学", "13900139000", "free", null, "user", "student", "", ""},
                    {"admin", SecurityUtil.hash("admin","admin123456"), "平台管理员", "苏州科技大学", "", "free", null, "admin", "teacher", "ADMIN001", "信息化建设与管理中心"}
                };
                for (Object[] u : users) {
                    for (int i = 0; i < u.length; i++) ps.setObject(i+1, u[i]);
                    ps.executeUpdate();
                }
            }
            // 示例活动
            try (var ps = conn.prepareStatement(
                    "INSERT INTO activities(name,category,description,date,time,location,school,max_people,fee,publisher,tags,promoted,promote_plan,promote_days,credit_hours) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                Object[][] acts = sampleActivities();
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
