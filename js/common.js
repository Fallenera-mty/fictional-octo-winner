// 公共工具：全站共享的登录检查、用户区、时钟、API 请求、HTML 转义

// ==================== HTML 转义（防 XSS） ====================
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// ==================== 带登录态的 API 请求 ====================
async function apiFetch(url, options = {}) {
    const token = localStorage.getItem('campus_token');
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const resp = await fetch(url, Object.assign({}, options, { headers }));
    return resp.json();
}

// ==================== 登录检查 ====================
function guardLogin() {
    if (!localStorage.getItem('campus_logged_in')) {
        window.location.href = '../html/login.html';
    }
}

// ==================== 用户区：显示昵称 + 点击进个人中心 + 退出 ====================
function initUserArea() {
    const account = localStorage.getItem('campus_account');
    const userSpan = document.getElementById('userAccount');
    if (userSpan) {
        userSpan.textContent = account || '用户';
        userSpan.title = '点击进入个人中心';
        userSpan.style.cursor = 'pointer';
        userSpan.addEventListener('click', () => {
            window.location.href = '../html/profile.html';
        });
    }

    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            try {
                await fetch('/api/logout', {
                    method: 'POST',
                    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('campus_token') }
                });
            } catch (e) {}
            ['campus_logged_in', 'campus_account', 'campus_token', 'campus_user'].forEach(k => localStorage.removeItem(k));
            window.location.href = '../html/login.html';
        });
    }

    // 老师/管理员：显示“发布活动”；学生隐藏
    try {
        const user = JSON.parse(localStorage.getItem('campus_user') || 'null');
        applyRoleNav(user);
        apiFetch('/api/user').then(d => {
            const u = d.user || d;
            if (u && u.account) {
                localStorage.setItem('campus_user', JSON.stringify(u));
                applyRoleNav(u);
            }
        }).catch(() => {});
    } catch (e) {}

    // 管理员：导航栏动态插入“后台”入口
    try {
        const user = JSON.parse(localStorage.getItem('campus_user') || 'null');
        if (user && user.role === 'admin') {
            const navLinks = document.querySelector('.nav-links');
            if (navLinks && !document.getElementById('adminNavLink')) {
                const a = document.createElement('a');
                a.href = '../html/admin.html';
                a.id = 'adminNavLink';
                a.textContent = '后台';
                navLinks.appendChild(a);
            }
        }
    } catch (e) {}
}

// 根据身份显示/隐藏发布活动入口
function applyRoleNav(user) {
    const canPublish = !!user && (user.userType === 'teacher' || user.role === 'admin');
    document.querySelectorAll('.nav-publish').forEach(a => {
        a.style.display = canPublish ? '' : 'none';
    });
}

// ==================== 系统时间动态显示（每秒更新） ====================
function initClock() {
    const navClock = document.getElementById('navClock');
    if (!navClock) return;

    function updateClock() {
        const now = new Date();
        const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
        const pad = (n) => String(n).padStart(2, '0');
        navClock.textContent =
            now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) +
            ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds()) +
            ' 星期' + week;
    }

    updateClock();
    setInterval(updateClock, 1000);
}