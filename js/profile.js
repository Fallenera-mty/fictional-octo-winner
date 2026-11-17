// 个人中心页交互逻辑

guardLogin();
initUserArea();
initClock();

const categoryMap = {
    lecture: '讲座论坛', sports: '体育竞技', art: '文艺演出',
    tech: '科技创新', volunteer: '志愿公益', social: '社交聚会'
};

// ==================== 加载个人资料 ====================
async function loadProfile() {
    try {
        const data = await apiFetch('/api/user');
        if (!data.success) return;

        const u = data.user;
        document.getElementById('pAccount').value = u.account || '';
        document.getElementById('pNickname').value = u.nickname || '';
        document.getElementById('pSchool').value = u.school || '';
        document.getElementById('pPhone').value = u.phone || '';

        // 平台身份（校内公益免费）
        const identity = u.userType === 'teacher'
            ? `${u.college ? u.college + ' · ' : ''}老师`
            : '学生';
        document.getElementById('vipBanner').textContent = `苏州科技大学 · ${identity} · 校内公益免费平台`;
    } catch (err) {
        console.error('加载资料失败', err);
    }
}

// ==================== 个人数据概览 ====================
async function loadStats() {
    try {
        const data = await apiFetch('/api/user/stats');
        if (!data.success) return;
        document.getElementById('statHours').textContent = data.totalHours || 0;
        document.getElementById('statAttended').textContent = data.attended || 0;
        document.getElementById('statAbsent').textContent = data.absent || 0;
        document.getElementById('statTotal').textContent = data.totalSignups || 0;
    } catch (err) {
        console.error('加载个人统计失败', err);
    }
}

// ==================== 保存基本资料 ====================
document.getElementById('profileForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('saveProfileBtn');
    if (btn.disabled) return;

    const nickname = document.getElementById('pNickname').value.trim();
    if (!nickname) {
        alert('昵称不能为空');
        return;
    }

    btn.disabled = true;
    btn.textContent = '保存中...';

    try {
        const data = await apiFetch('/api/user/update', {
            method: 'POST',
            body: JSON.stringify({
                nickname: nickname,
                school: document.getElementById('pSchool').value.trim(),
                phone: document.getElementById('pPhone').value.trim()
            })
        });

        if (data.success) {
            alert('资料保存成功');
        } else {
            alert(data.message || '保存失败');
        }
    } catch (err) {
        alert('网络错误');
    } finally {
        btn.disabled = false;
        btn.textContent = '保存资料';
    }
});

// ==================== 修改密码 ====================
const newPwdInput = document.getElementById('newPwd');
const newPwd2Input = document.getElementById('newPwd2');
const pwdErr = document.getElementById('pwdErr');
const pwd2Err = document.getElementById('pwd2Err');

// 实时校验
newPwdInput.addEventListener('input', () => {
    const v = newPwdInput.value;
    pwdErr.hidden = v.length === 0 || (v.length >= 8 && v.length <= 16);
    if (newPwd2Input.value.length > 0) {
        pwd2Err.hidden = newPwd2Input.value === v;
    }
});

newPwd2Input.addEventListener('input', () => {
    pwd2Err.hidden = newPwd2Input.value === newPwdInput.value;
});

document.getElementById('pwdForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('savePwdBtn');
    if (btn.disabled) return;

    const oldPwd = document.getElementById('oldPwd').value;
    const newPwd = newPwdInput.value;
    const newPwd2 = newPwd2Input.value;

    // 整体校验
    let ok = true;
    if (!(newPwd.length >= 8 && newPwd.length <= 16)) {
        pwdErr.hidden = false;
        ok = false;
    }
    if (newPwd !== newPwd2) {
        pwd2Err.hidden = false;
        ok = false;
    }
    if (!oldPwd) {
        alert('请输入旧密码');
        return;
    }
    if (!ok) return;

    btn.disabled = true;
    btn.textContent = '提交中...';

    try {
        const data = await apiFetch('/api/user/update', {
            method: 'POST',
            body: JSON.stringify({ oldPassword: oldPwd, newPassword: newPwd })
        });

        if (data.success) {
            alert('密码修改成功，请牢记新密码');
            document.getElementById('pwdForm').reset();
            pwdErr.hidden = true;
            pwd2Err.hidden = true;
        } else {
            alert(data.message || '修改失败');
        }
    } catch (err) {
        alert('网络错误');
    } finally {
        btn.disabled = false;
        btn.textContent = '修改密码';
    }
});

// ==================== 三个活动列表 ====================
function renderActRow(act, showUnfav) {
    const catName = categoryMap[act.category] || act.category;
    const hours = act.creditHours || 0;
    const feeText = hours > 0 ? `${hours} 学时` : '无学时';
    const feeClass = hours > 0 ? 'row-fee' : 'row-fee free';

    return `
        <div class="act-row" data-id="${act.id}">
            <span class="row-tag">${escapeHtml(catName)}</span>
            <span class="row-name">${escapeHtml(act.name)}</span>
            <span class="row-meta">${escapeHtml(act.school)} · ${act.date}</span>
            <span class="${feeClass}">${feeText}</span>
            ${showUnfav ? `<button class="row-unfav" data-id="${act.id}" title="取消收藏">♥</button>` : ''}
        </div>
    `;
}

async function loadList(apiPath, listId, countId, emptyText, showUnfav) {
    const listEl = document.getElementById(listId);
    try {
        const data = await apiFetch(apiPath);
        const list = Array.isArray(data) ? data : [];

        document.getElementById(countId).textContent = list.length ? `共 ${list.length} 条` : '';

        if (list.length === 0) {
            listEl.innerHTML = `<div class="empty-tip">${emptyText}</div>`;
            return;
        }

        listEl.innerHTML = list.map(a => renderActRow(a, showUnfav)).join('');

        // 点击行跳详情页
        listEl.querySelectorAll('.act-row').forEach(row => {
            row.addEventListener('click', (e) => {
                if (e.target.closest('.row-unfav')) return;
                window.location.href = '../html/home.html';
            });
        });

        // 取消收藏按钮
        if (showUnfav) {
            listEl.querySelectorAll('.row-unfav').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    if (btn.disabled) return;
                    btn.disabled = true;
                    try {
                        const data = await apiFetch(`/api/activities/${btn.dataset.id}/favorite`, { method: 'POST' });
                        if (data.success && !data.favorited) {
                            const row = btn.closest('.act-row');
                            row.remove();
                            // 更新计数
                            const cnt = listEl.querySelectorAll('.act-row').length;
                            document.getElementById(countId).textContent = cnt ? `共 ${cnt} 条` : '';
                            if (cnt === 0) {
                                listEl.innerHTML = `<div class="empty-tip">暂无收藏的活动</div>`;
                            }
                        }
                    } catch (err) {
                        alert('网络错误');
                    } finally {
                        btn.disabled = false;
                    }
                });
            });
        }
    } catch (err) {
        listEl.innerHTML = '<div class="empty-tip">加载失败，请刷新重试</div>';
    }
}

// ==================== 我的日历 ====================
const CAL_GROUPS = [
    { key: 'today', title: '今天' },
    { key: 'week', title: '本周内' },
    { key: 'later', title: '更晚' },
    { key: 'ended', title: '已结束' }
];

// 活动结束时刻：优先用结束时间，缺省按当天 23:59
function activityEndDate(act) {
    if (!act.date) return null;
    const t = act.endTime || '23:59';
    const d = new Date(`${act.date}T${t}:00`);
    return isNaN(d) ? new Date(act.date + 'T23:59:00') : d;
}

// 距今天数：0=今天，1=明天，负数=过去
function dayDiff(dateStr) {
    const d = new Date(dateStr + 'T00:00:00');
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((d - today) / 86400000);
}

function calendarGroupOf(act) {
    const end = activityEndDate(act);
    if (end && end < new Date()) return 'ended';
    const diff = dayDiff(act.date);
    if (diff <= 0) return 'today';
    if (diff <= 7) return 'week';
    return 'later';
}

function calendarBadge(act, group) {
    if (group === 'ended') return { text: '已结束', cls: 'ended' };
    const diff = dayDiff(act.date);
    if (diff === 0) return { text: '今天', cls: 'today' };
    if (diff === 1) return { text: '明天', cls: 'soon' };
    return { text: `还有 ${diff} 天`, cls: 'soon' };
}

function renderCalendarRow(act, group) {
    const hours = act.creditHours || 0;
    const badge = calendarBadge(act, group);
    return `
        <div class="cal-row${group === 'ended' ? ' expired' : ''}" data-id="${act.id}">
            <div class="cal-date">
                <span class="cal-day">${escapeHtml(act.date)}</span>
                <span class="cal-time">${escapeHtml(act.time)}${act.endTime ? ' - ' + escapeHtml(act.endTime) : ''}</span>
            </div>
            <div class="cal-info">
                <div class="cal-title">
                    <span class="cal-name">${escapeHtml(act.name)}</span>
                    <span class="cal-badge ${badge.cls}">${badge.text}</span>
                </div>
                <div class="cal-meta">${escapeHtml(act.location)} · ${escapeHtml(act.school)}${hours > 0 ? ' · ' + hours + ' 学时' : ''}</div>
            </div>
            <button class="cal-remove" data-id="${act.id}" title="从日历移出">移出</button>
        </div>
    `;
}

async function loadCalendar() {
    const listEl = document.getElementById('calendarList');
    try {
        const data = await apiFetch('/api/user/calendar');
        const list = Array.isArray(data) ? data : [];

        document.getElementById('calendarCount').textContent = list.length ? `共 ${list.length} 个安排` : '';

        if (list.length === 0) {
            listEl.innerHTML = '<div class="empty-tip">还没有加入日历的活动，去首页详情页点「加入我的日历」</div>';
            return;
        }

        // 按今天 / 本周内 / 更晚 / 已结束分组，组内保持接口的时间顺序
        const grouped = { today: [], week: [], later: [], ended: [] };
        list.forEach(a => grouped[calendarGroupOf(a)].push(a));

        listEl.innerHTML = CAL_GROUPS
            .filter(g => grouped[g.key].length > 0)
            .map(g => `
                <div class="cal-group">
                    <div class="cal-group-head">${g.title}<span>${grouped[g.key].length}</span></div>
                    ${grouped[g.key].map(a => renderCalendarRow(a, g.key)).join('')}
                </div>
            `)
            .join('');

        listEl.querySelectorAll('.cal-remove').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                if (btn.disabled) return;
                btn.disabled = true;
                try {
                    const res = await apiFetch(`/api/activities/${btn.dataset.id}/calendar`, { method: 'POST' });
                    if (res.success && !res.inCalendar) {
                        loadCalendar();
                    } else {
                        btn.disabled = false;
                    }
                } catch (err) {
                    alert('网络错误');
                    btn.disabled = false;
                }
            });
        });
    } catch (err) {
        listEl.innerHTML = '<div class="empty-tip">加载失败，请刷新重试</div>';
    }
}

// ==================== 初始化 ====================
loadProfile();
loadStats();
loadCalendar();
loadList('/api/user/signups', 'signupList', 'signupCount', '暂无报名的活动', false);
loadList('/api/user/favorites', 'favList', 'favCount', '暂无收藏的活动', true);
loadList('/api/user/activities', 'publishList', 'publishCount', '暂无发布的活动', false);