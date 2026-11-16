# 一次性数据迁移脚本：旧库升级（新列、未来日期、admin账号、明文密码升级、演示数据）
import sqlite3, hashlib

DB = 'campus_activity.db'

def h(account, pwd):
    return 'sha256:' + hashlib.sha256((account + ':' + pwd).encode('utf-8')).hexdigest()

conn = sqlite3.connect(DB)
cur = conn.cursor()

def add_col(table, col_def):
    cur.execute(f"PRAGMA table_info({table})")
    cols = [r[1] for r in cur.fetchall()]
    name = col_def.split()[0]
    if name not in cols:
        cur.execute(f"ALTER TABLE {table} ADD COLUMN {col_def}")
        print(f'已添加列: {table}.{name}')

add_col('users', "role TEXT DEFAULT 'user'")
add_col('users', "banned INTEGER DEFAULT 0")
add_col('activities', "status INTEGER DEFAULT 1")

cur.execute("""CREATE TABLE IF NOT EXISTS comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id INTEGER NOT NULL,
    account TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s','now')*1000)
)""")
cur.execute("CREATE INDEX IF NOT EXISTS idx_comment_activity ON comments(activity_id)")

# 活动日期更新到未来（与新版种子数据一致）
future_dates = {
    1: '2026-12-05', 2: '2026-12-12', 3: '2026-12-20', 4: '2027-01-05',
    5: '2027-01-10', 6: '2026-11-28', 7: '2026-12-08', 8: '2026-12-15'
}
for aid, d in future_dates.items():
    cur.execute("UPDATE activities SET date=? WHERE id=?", (d, aid))
print('活动日期已更新为未来日期')

# admin 账号
cur.execute("SELECT 1 FROM users WHERE account='admin'")
if not cur.fetchone():
    cur.execute("INSERT INTO users(account,password,nickname,school,phone,vip_level,vip_expire,role) VALUES(?,?,?,?,?,?,?,?)",
                ('admin', h('admin', 'admin123456'), '平台管理员', '花海平台', '', 'free', None, 'admin'))
    print('已创建管理员账号 admin')

# 明文密码升级为哈希
for acc, pwd in [('demo', '12345678'), ('test', '12345678')]:
    cur.execute("SELECT password FROM users WHERE account=?", (acc,))
    row = cur.fetchone()
    if row and row[0] == pwd:
        cur.execute("UPDATE users SET password=? WHERE account=?", (h(acc, pwd), acc))
        print(f'已升级明文密码: {acc}')

# 过期演示活动
cur.execute("SELECT 1 FROM activities WHERE name LIKE '【已结束】%'")
if not cur.fetchone():
    cur.execute("INSERT INTO activities(name,category,description,date,time,location,school,max_people,fee,publisher,tags) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        ('【已结束】2025春季社团招新回顾展', 'social', '往期活动回顾展示，用于演示过期活动不可报名的平台规则。',
         '2025-06-01', '09:00', '北京大学 逸夫楼', '北京大学', 100, 0, 'demo', '往期,回顾'))
    print('已插入过期演示活动')

# 满员演示活动
cur.execute("SELECT 1 FROM activities WHERE name LIKE '【已满员】%'")
if not cur.fetchone():
    cur.execute("INSERT INTO activities(name,category,description,date,time,location,school,max_people,fee,publisher,tags) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        ('【已满员】新生跨校线上见面会', 'social', '新生线上破冰见面会，名额有限先到先得，用于演示人数上限的平台规则。',
         '2026-11-20', '19:00', '线上 腾讯会议', '花海平台', 1, 0, 'demo', '新生,见面会'))
    cur.execute("INSERT INTO signups(activity_id,account) VALUES(?,?)", (cur.lastrowid, 'demo'))
    print('已插入满员演示活动')

# 评论种子
cur.execute("SELECT COUNT(*) FROM comments WHERE activity_id=1")
if cur.fetchone()[0] == 0:
    cur.execute("INSERT INTO comments(activity_id,account,content) VALUES(?,?,?)",
                (1, 'demo', '已经报名了，期待和大佬们面对面交流！'))
    cur.execute("INSERT INTO comments(activity_id,account,content) VALUES(?,?,?)",
                (1, 'test', '求组队一起去，评论区滴滴我~'))
    print('已插入评论种子')

conn.commit()
conn.close()
print('迁移完成')