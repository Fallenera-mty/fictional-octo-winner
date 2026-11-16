// 管理后台交互逻辑 - 活动审核上下架、用户封禁、数据总览
// 公共部分（登录检查、用户区、时钟、退出登录）在 common.js 中

guardLogin();

// 管理员身份校验：非管理员直接踢回主页（后端接口还有二次校验）
const adminUser = JSON.parse(localStorage.getItem('campus_user') || 'null');
if (!adminUser || adminUser.role !== 'admin') {
    window.location.href = '../html/home.html';
}

initUserArea();
initClock();

const categoryMap = {
    lecture: '讲座论坛', sports: '体育竞技', art: '文艺演出',
    tech: '科技创新', volunteer: '志愿公益', social: '社交聚会'
};

const vipNameMap = {
    free: '普通用户', campus: '校园会员', premium: '高级会员'
};

// ==================== 统计卡片 ====================
async function loadStats() {
    try {
        const resp = await fetch('/api/stats');
        const stats = await resp.json();
        if (stats.totalUsers === undefined) return;
        document.getElementById('statUsers').textContent = stats.totalUsers ?? '-';
        document.getElementById('statActivities').textContent = stats.totalActivities ?? '-';
        document.getElementById('statSignups').textContent = stats.totalSignups ?? '-';
        document.getElementById('statRatings').textContent = stats.totalRatings ?? '-';
    } catch (err) {
        console.error('统计加载失败', err);
    }
}

// ==================== 活动管理 ====================
async function loadActivities() {
    const tbody = document.querySelector('#activityTable tbody');
    try {
        const data = await apiFetch('/api/admin/activities');
        const list = Array.isArray(data) ? data : [];
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="table-loading">暂无活动</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(a => `
            <tr>
                <td>${a.id}</td>
                <td class="cell-name">${escapeHtml(a.name)}</td>
                <td>${categoryMap[a.category] || a.category}</td>
                <td>${escapeHtml(a.publisher)}</td>
                <td>${a.date}</td>
                <td>${a.signupCount || 0}${a.maxPeople > 0 ? ' / ' + a.maxPeople : ''}</td>
                <td>
                    ${a.status === 1
                        ? '<span class="status-badge status-on">已上架</span>'
                        : '<span class="status-badge status-off">已下架</span>'}
                </td>
                <td class="cell-actions">
                    ${a.status === 1
                        ? `<button class="btn btn-ghost btn-sm act-off" data-id="${a.id}">下架</button>`
                        : `<button class="btn btn-success btn-sm act-on" data-id="${a.id}">上架</button>`}
                    <button class="btn btn-danger btn-sm act-del" data-id="${a.id}">删除</button>
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.act-off').forEach(btn => {
            btn.addEventListener('click', () => updateActivityStatus(btn.dataset.id, 0));
        });
        tbody.querySelectorAll('.act-on').forEach(btn => {
            btn.addEventListener('click', () => updateActivityStatus(btn.dataset.id, 1));
        });
        tbody.querySelectorAll('.act-del').forEach(btn => {
            btn.addEventListener('click', () => deleteActivity(btn.dataset.id, btn));
        });
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="8" class="table-loading">加载失败，请确认管理员身份</td></tr>';
    }
}

async function updateActivityStatus(id, status) {
    const action = status === 1 ? '上架' : '下架';
    if (!confirm(`确认${action}该活动？`)) return;
    try {
        const data = await apiFetch(`/api/admin/activities/${id}/status`, {
            method: 'POST',
            body: JSON.stringify({ status })
        });
        if (data.success) {
            loadActivities();
            loadStats();
        } else {
            alert(data.message || '操作失败');
        }
    } catch (err) {
        alert('网络错误');
    }
}

async function deleteActivity(id, btn) {
    if (!confirm('确认删除该活动？删除后其报名、评价、收藏数据都会清除，不可恢复！')) return;
    btn.disabled = true;
    try {
        const data = await apiFetch(`/api/admin/activities/${id}/delete`, { method: 'POST' });
        if (data.success) {
            loadActivities();
            loadStats();
        } else {
            alert(data.message || '删除失败');
        }
    } catch (err) {
        alert('网络错误');
    }
    btn.disabled = false;
}

// ==================== 用户管理 ====================
async function loadUsers() {
    const tbody = document.querySelector('#userTable tbody');
    try {
        const data = await apiFetch('/api/admin/users');
        const list = Array.isArray(data) ? data : [];
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="table-loading">暂无用户</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(u => `
            <tr>
                <td>${escapeHtml(u.account)}</td>
                <td>${escapeHtml(u.nickname)}</td>
                <td>${escapeHtml(u.school || '-')}</td>
                <td>${vipNameMap[u.vipLevel] || u.vipLevel}</td>
                <td>${u.role === 'admin' ? '<span class="status-badge status-admin">管理员</span>' : '普通用户'}</td>
                <td>
                    ${u.banned
                        ? '<span class="status-badge status-off">已封禁</span>'
                        : '<span class="status-badge status-on">正常</span>'}
                </td>
                <td class="cell-actions">
                    ${u.role === 'admin' ? '-' : (u.banned
                        ? `<button class="btn btn-success btn-sm user-unban" data-account="${escapeHtml(u.account)}">解封</button>`
                        : `<button class="btn btn-danger btn-sm user-ban" data-account="${escapeHtml(u.account)}">封禁</button>`)}
                </td>
            </tr>
        `).join('');

        tbody.querySelectorAll('.user-ban').forEach(btn => {
            btn.addEventListener('click', () => setUserBanned(btn.dataset.account, true));
        });
        tbody.querySelectorAll('.user-unban').forEach(btn => {
            btn.addEventListener('click', () => setUserBanned(btn.dataset.account, false));
        });
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="7" class="table-loading">加载失败，请确认管理员身份</td></tr>';
    }
}

async function setUserBanned(account, banned) {
    const action = banned ? '封禁' : '解封';
    if (!confirm(`确认${action}用户「${account}」？`)) return;
    const path = banned ? 'ban' : 'unban';
    try {
        const data = await apiFetch(`/api/admin/users/${encodeURIComponent(account)}/${path}`, { method: 'POST' });
        if (data.success) {
            loadUsers();
        } else {
            alert(data.message || '操作失败');
        }
    } catch (err) {
        alert('网络错误');
    }
}

// ==================== 初始化 ====================
document.getElementById('refreshActivitiesBtn').addEventListener('click', loadActivities);
document.getElementById('refreshUsersBtn').addEventListener('click', loadUsers);

loadStats();
loadActivities();
loadUsers();