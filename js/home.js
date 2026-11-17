// 主页面交互逻辑 - 活动列表 + 详情
// 公共部分（登录检查、用户区、时钟、退出登录）在 common.js 中

guardLogin();
initUserArea();
initClock();

const token = localStorage.getItem('campus_token');
const account = localStorage.getItem('campus_account');

// ==================== 数据 ====================
const categoryMap = {
    lecture: '讲座论坛', sports: '体育竞技', art: '文艺演出',
    tech: '科技创新', volunteer: '志愿公益', social: '社交聚会'
};

const categoryColors = {
    lecture: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    sports: 'linear-gradient(135deg, #f59e0b, #f97316)',
    art: 'linear-gradient(135deg, #ec4899, #f43f5e)',
    tech: 'linear-gradient(135deg, #06b6d4, #3b82f6)',
    volunteer: 'linear-gradient(135deg, #22c55e, #10b981)',
    social: 'linear-gradient(135deg, #a855f7, #6366f1)'
};

const categoryTagClass = {
    lecture: 'tag-warning', sports: 'tag-success', art: 'tag-danger',
    tech: 'tag-primary', volunteer: 'tag-success', social: 'tag-primary'
};

let allActivities = [];
let currentActivity = null;
let selectedRating = 0;
let checkinStatus = null;   // 当前活动的签到状态
let currentConflicts = [];  // 当前活动与已报名活动的时间冲突
let favIds = new Set();   // 我收藏的活动id集合
// 发布成功后跳转带来的新活动 id，用于高亮并滚动定位
let pendingNewId = parseInt(new URLSearchParams(location.search).get('new'), 10) || null;

// ==================== 加载收藏状态 ====================
async function loadFavorites() {
    try {
        const data = await apiFetch('/api/user/favorites');
        const list = Array.isArray(data) ? data : [];
        favIds = new Set(list.map(a => a.id));
    } catch (err) {
        favIds = new Set();
    }
}

// ==================== 加载活动列表 ====================
async function loadActivities(keyword, category) {
    // 显示加载提示，请求回来后被渲染内容替换
    const activityGrid = document.getElementById('activityGrid');
    activityGrid.innerHTML = '<div class="loading-tip">加载中...</div>';

    try {
        let url = '/api/activities';
        const params = [];
        if (keyword) params.push('keyword=' + encodeURIComponent(keyword));
        if (category && category !== 'all') params.push('category=' + encodeURIComponent(category));
        if (params.length) url += '?' + params.join('&');

        const resp = await fetch(url, { cache: 'no-store' });
        const data = await resp.json();
        allActivities = Array.isArray(data) ? data : [];
        renderActivities();
    } catch (err) {
        console.error('加载活动失败', err);
        allActivities = [];
        activityGrid.innerHTML = '<div class="loading-tip">加载失败，请稍后重试</div>';
        return;
    }
}

// ==================== 渲染活动列表 ====================
function renderActivities() {
    const promoted = allActivities.filter(a => a.promoted);
    const normal = allActivities.filter(a => !a.promoted);

    // 首推活动
    const promoteSection = document.getElementById('promoteSection');
    const promoteGrid = document.getElementById('promoteGrid');
    if (promoted.length > 0) {
        promoteSection.style.display = '';
        promoteGrid.innerHTML = promoted.map(a => renderCard(a, true)).join('');
    } else {
        promoteSection.style.display = 'none';
    }

    // 普通活动
    const activityGrid = document.getElementById('activityGrid');
    activityGrid.innerHTML = normal.map(a => renderCard(a, false)).join('');

    // 绑定卡片事件（含推荐区卡片，防重复绑定）
    bindCardEvents();

    // 发布成功后高亮并定位新活动
    highlightNewActivity();
}

function highlightNewActivity() {
    if (!pendingNewId) return;
    const card = document.querySelector(`.activity-card[data-id="${pendingNewId}"]`);
    if (!card) return;
    card.classList.add('just-published');
    setTimeout(() => card.scrollIntoView({ behavior: 'smooth', block: 'center' }), 120);
    // 清掉 URL 参数，避免刷新后重复高亮
    const url = new URL(location.href);
    url.searchParams.delete('new');
    history.replaceState(null, '', url.pathname + (url.search || ''));
    pendingNewId = null;
}

function bindCardEvents() {
    document.querySelectorAll('.activity-card').forEach(card => {
        if (card.dataset.bound) return;
        card.dataset.bound = '1';
        card.addEventListener('click', (e) => {
            if (e.target.closest('.fav-btn')) return;   // 点的是收藏按钮就不选详情
            e.preventDefault();
            const id = parseInt(card.dataset.id);
            selectActivity(id);
        });
    });

    document.querySelectorAll('.fav-btn').forEach(btn => {
        if (btn.dataset.bound) return;
        btn.dataset.bound = '1';
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            e.preventDefault();
            if (btn.disabled) return;
            const id = parseInt(btn.dataset.id);
            btn.disabled = true;   // 防止快速连点

            try {
                const data = await apiFetch(`/api/activities/${id}/favorite`, { method: 'POST' });
                if (data.success) {
                    if (data.favorited) {
                        favIds.add(id);
                        btn.classList.add('active');
                        btn.textContent = '♥';
                        btn.title = '取消收藏';
                    } else {
                        favIds.delete(id);
                        btn.classList.remove('active');
                        btn.textContent = '♡';
                        btn.title = '收藏活动';
                    }
                }
            } catch (err) {
                alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
            } finally {
                btn.disabled = false;
            }
        });
    });
}

function renderCard(act, isPromote) {
    const catName = categoryMap[act.category] || act.category;
    const tagClass = categoryTagClass[act.category] || 'tag-primary';
    const hours = act.creditHours || 0;
    const priceText = hours > 0 ? `${hours} 学时` : '无学时';
    const priceClass = hours > 0 ? 'price hours' : 'price free';
    const selectedClass = currentActivity && currentActivity.id === act.id ? ' selected' : '';
    const favorited = favIds.has(act.id);

    return `
        <div class="activity-card${isPromote ? ' promote-card' : ''}${selectedClass}" data-id="${act.id}" data-cat="${act.category}">
            <div class="card-banner" style="background: ${categoryColors[act.category] || categoryColors.tech}">
                ${isPromote ? '<span class="promote-badge">推荐</span>' : ''}
                <span class="tag ${tagClass}">${catName}</span>
                <button class="fav-btn${favorited ? ' active' : ''}" data-id="${act.id}" title="${favorited ? '取消收藏' : '收藏活动'}">${favorited ? '♥' : '♡'}</button>
            </div>
            <div class="card-body">
                <h3>${escapeHtml(act.name)}</h3>
                <div class="card-meta">
                    <span class="meta-item">${escapeHtml(act.school)}</span>
                    <span class="meta-item">${act.date}</span>
                </div>
                <div class="card-footer">
                    <span class="${priceClass}">${priceText}</span>
                    <span class="participants">${act.signupCount || 0}人已报名</span>
                </div>
            </div>
        </div>
    `;
}


// ==================== 选中活动 - 展示详情 ====================
async function selectActivity(id) {
    try {
        const resp = await fetch(`/api/activities/${id}`);
        const data = await resp.json();
        if (!data.success) return;

        currentActivity = data.activity;
        checkinStatus = null;
        currentConflicts = [];
        renderDetail(data);
        loadCheckinStatus(data.activity.id);

        // 详情固定显示在右侧：窄屏时自动滑入，并回到详情顶部
        const panel = document.getElementById('detailPanel');
        panel.classList.add('open');
        panel.scrollTop = 0;
    } catch (err) {
        console.error('加载活动详情失败', err);
    }
}

function renderDetail(data) {
    const act = data.activity;
    const signupAccounts = act.signupAccounts || [];

    // 显示详情面板
    document.getElementById('detailEmpty').hidden = true;
    document.getElementById('detailContent').hidden = false;
    document.getElementById('detailPanel').classList.remove('empty');

    // 头图
    const banner = document.getElementById('detailBanner');
    banner.style.background = categoryColors[act.category] || categoryColors.tech;

    const overlay = document.getElementById('detailBannerOverlay');
    const catName = categoryMap[act.category] || act.category;
    const tagClass = categoryTagClass[act.category] || 'tag-primary';
    overlay.innerHTML = `
        ${act.promoted ? '<span class="promote-badge">推荐</span>' : ''}
        <span class="tag ${tagClass}" style="font-size:13px;">${catName}</span>
    `;

    // 标题
    document.getElementById('detailTitle').textContent = act.name;

    // 元信息
    const metaEl = document.getElementById('detailMeta');
    const waitlistCount = act.waitlistCount || 0;
    const waitlistText = waitlistCount > 0 ? ` · 候补 ${waitlistCount} 人` : '';
    const maxText = (act.maxPeople > 0 ? `${signupAccounts.length} / ${act.maxPeople} 人` : `${signupAccounts.length} 人`) + waitlistText;
    metaEl.innerHTML = `
        <div class="meta-row"><span class="meta-label">发布者</span><span class="meta-value">${escapeHtml(act.publisher)}</span></div>
        <div class="meta-row"><span class="meta-label">时间</span><span class="meta-value">${act.date} ${act.time}${act.endTime ? ' - ' + act.endTime : ''}</span></div>
        <div class="meta-row"><span class="meta-label">地点</span><span class="meta-value">${escapeHtml(act.location)}</span></div>
        <div class="meta-row"><span class="meta-label">学校</span><span class="meta-value">${escapeHtml(act.school)}</span></div>
        <div class="meta-row"><span class="meta-label">已报名</span><span class="meta-value">${maxText}</span></div>
    `;

    // 描述
    document.getElementById('detailDesc').textContent = act.description || '暂无介绍';

    // 标签
    const tagsEl = document.getElementById('detailTags');
    if (act.tags) {
        tagsEl.innerHTML = act.tags.split(',').map(t => `<span class="tag tag-primary">${escapeHtml(t.trim())}</span>`).join('');
    } else {
        tagsEl.innerHTML = '';
    }

    // 学时奖励
    const priceEl = document.getElementById('detailPrice');
    const hours = act.creditHours || 0;
    priceEl.textContent = hours > 0 ? `${hours} 学时` : '无学时';
    priceEl.classList.toggle('free', hours === 0);

    // 报名信息
    const signupInfoEl = document.getElementById('detailSignupInfo');
    signupInfoEl.innerHTML = hours > 0
        ? `<span>参加并完成签到，可得 ${hours} 学时</span>`
        : '<span>校内免费活动 · 暂无学时奖励</span>';

    // 报名按钮状态（过期活动不可报名；满员显示加入候补）
    const signupBtn = document.getElementById('signupBtn');
    const isSignedUp = signupAccounts.includes(account);
    const expired = isActivityEnded(act);
    const full = act.maxPeople > 0 && signupAccounts.length >= act.maxPeople;
    if (isSignedUp) {
        signupBtn.textContent = '已报名';
        signupBtn.disabled = true;
        signupBtn.style.background = 'var(--success)';
    } else if (expired) {
        signupBtn.textContent = '活动已结束';
        signupBtn.disabled = true;
        signupBtn.style.background = 'var(--gray-300)';
    } else {
        signupBtn.textContent = full ? '加入候补' : '立即报名';
        signupBtn.disabled = false;
        signupBtn.style.background = '';
    }

    // 加入「我的」日历（保存在账号里，不再生成/下载本地日历文件）
    renderCalendarBtn(act);

    // 报名状态相关的按钮先复位，具体由 loadCheckinStatus 决定
    document.getElementById('cancelSignupBtn').hidden = true;
    document.getElementById('conflictNotice').hidden = true;
    if (!isSignedUp && !expired) loadConflicts(act.id);

    // 评分
    const avgRating = act.avgRating || 0;
    const ratingCount = act.ratingCount || 0;
    const currentRatingEl = document.getElementById('currentRating');
    const starsHtml = renderStars(avgRating);
    currentRatingEl.innerHTML = `
        <div class="stars">${starsHtml}</div>
        <span class="rating-num">${avgRating.toFixed(1)} / 5.0</span>
        <span class="rating-count">(${ratingCount}人评分)</span>
    `;

    // 评分区域：需已报名 + 已签到 + 活动已开始
    renderRatingGate(act);

    // 高亮选中的卡片
    document.querySelectorAll('.activity-card').forEach(card => {
        card.classList.toggle('selected', parseInt(card.dataset.id) === act.id);
    });
}

// 活动是否已结束：优先按结束时间判断，缺省按日期判断
function isActivityEnded(act) {
    const today = new Date().toISOString().slice(0, 10);
    if (!act.endTime) return (act.date || '') < today;
    const end = new Date(`${act.date}T${act.endTime}:00`);
    return !isNaN(end) && end < new Date();
}

function renderCalendarBtn(act) {
    const btn = document.getElementById('calendarBtn');
    const inCalendar = !!act.inCalendar;
    btn.textContent = inCalendar ? '已加入我的日历 · 点击移出' : '加入我的日历';
    btn.classList.toggle('added', inCalendar);
    btn.title = (inCalendar ? '已加入：' : '添加到「我的」日历：') +
        `${act.date} ${act.time} · ${act.school} ${act.location}`;
}

function renderStars(avg) {
    let html = '';
    for (let i = 1; i <= 5; i++) {
        html += i <= Math.round(avg) ? '<span>★</span>' : '<span class="empty">★</span>';
    }
    return html;
}

// ==================== 现场签到 ====================
async function loadCheckinStatus(id) {
    try {
        const data = await apiFetch('/api/activities/' + id + '/checkin');
        checkinStatus = data.success ? data : null;
    } catch (err) {
        checkinStatus = null;
    }
    // 状态回来时用户可能已切到别的活动，避免串台
    if (currentActivity && currentActivity.id === id) {
        renderCheckinCard(currentActivity);
        renderRatingGate(currentActivity);
        const cancelBtn = document.getElementById('cancelSignupBtn');
        if (checkinStatus && checkinStatus.signupStatus === 'waitlist') {
            const btn = document.getElementById('signupBtn');
            btn.textContent = '候补中（第 ' + checkinStatus.waitlistPosition + ' 位）';
            btn.disabled = true;
            btn.style.background = 'var(--gray-300)';
            cancelBtn.textContent = '退出候补';
            cancelBtn.hidden = false;
        } else if (checkinStatus && checkinStatus.signupStatus === 'confirmed') {
            cancelBtn.textContent = '取消报名';
            cancelBtn.hidden = false;
        } else {
            cancelBtn.hidden = true;
        }
    }
}

// 报名前的时间冲突提示（只展示，不阻断）
async function loadConflicts(id) {
    try {
        const conflicts = await apiFetch('/api/activities/' + id + '/conflicts');
        currentConflicts = Array.isArray(conflicts) ? conflicts : [];
    } catch (err) {
        currentConflicts = [];
    }
    const notice = document.getElementById('conflictNotice');
    if (currentConflicts.length && currentActivity && currentActivity.id === id) {
        notice.textContent = '时间冲突提醒：' +
            currentConflicts.map(c => `${c.date} ${c.time}${c.endTime ? '-' + c.endTime : ''} ${c.name}`).join('；') + '（仍可报名）';
        notice.hidden = false;
    } else {
        notice.hidden = true;
    }
}

function renderCheckinCard(act) {
    const card = document.getElementById('checkinCard');
    const statusText = document.getElementById('checkinStatusText');
    const attendee = document.getElementById('attendeeCheckin');
    const publisher = document.getElementById('publisherCheckin');
    if (!checkinStatus) { card.hidden = true; return; }

    card.hidden = false;
    attendee.hidden = true;
    publisher.hidden = true;
    document.getElementById('codeResult').hidden = true;

    if (checkinStatus.checkedIn) {
        statusText.textContent = '已完成现场签到，共 ' + (checkinStatus.checkedInCount || 0) + ' 人签到';
    } else if (checkinStatus.signupStatus === 'waitlist') {
        statusText.textContent = '你还在候补队列中（第 ' + checkinStatus.waitlistPosition + ' 位），递补成功后才可签到。';
    } else if (checkinStatus.publisher) {
        statusText.textContent = '你是本活动发布者，可生成签到码供到场同学输入（2 小时内有效）。';
        publisher.hidden = false;
    } else if (checkinStatus.signedUp && checkinStatus.withinCheckinWindow) {
        statusText.textContent = '请输入发布者出示的签到码完成签到。';
        attendee.hidden = false;
    } else if (checkinStatus.signedUp) {
        statusText.textContent = '当前不在签到时间内（活动开始前 1 小时至结束后 2 小时）。';
    } else {
        statusText.textContent = '报名后可在活动开始前 1 小时至结束后 2 小时内签到。';
    }
}

function renderRatingGate(act) {
    const myRatingEl = document.getElementById('myRating');
    const ratingLockedEl = document.getElementById('ratingLocked');
    const signedUp = checkinStatus
        ? checkinStatus.signupStatus === 'confirmed'
        : (act.signupAccounts || []).includes(account);
    const checkedIn = !!(checkinStatus && checkinStatus.checkedIn);
    const started = !!(checkinStatus && checkinStatus.started);

    let reason = '';
    if (checkinStatus && checkinStatus.signupStatus === 'waitlist') reason = '你还在候补队列中，递补成功并签到后才可评分';
    else if (!signedUp) reason = '报名并完成现场签到后才可评分';
    else if (!checkedIn) reason = '请先在活动现场完成签到，签到后才可评分';
    else if (!started) reason = '活动开始后才可评分';

    if (reason) {
        myRatingEl.hidden = true;
        ratingLockedEl.hidden = false;
        ratingLockedEl.querySelector('p').textContent = reason;
    } else {
        myRatingEl.hidden = false;
        ratingLockedEl.hidden = true;
        selectedRating = 0;
        updateStars();
    }
}

// 发布者：生成签到码
document.getElementById('genCodeBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    const btn = document.getElementById('genCodeBtn');
    btn.disabled = true;
    try {
        const data = await apiFetch('/api/activities/' + currentActivity.id + '/checkin/code', { method: 'POST' });
        if (data.success) {
            document.getElementById('checkinCodeDisplay').value = data.code;
            document.getElementById('codeResult').hidden = false;
            const d = new Date(data.expireAt);
            const pad = n => String(n).padStart(2, '0');
            document.getElementById('codeExpireText').textContent =
                '有效期至 ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        } else {
            alert(data.message || '生成失败');
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    }
    btn.disabled = false;
});

// 发布者：复制签到码
document.getElementById('copyCodeBtn').addEventListener('click', () => {
    const input = document.getElementById('checkinCodeDisplay');
    input.select();
    try {
        document.execCommand('copy');
        document.getElementById('copyCodeBtn').textContent = '已复制';
        setTimeout(() => { document.getElementById('copyCodeBtn').textContent = '复制签到码'; }, 1500);
    } catch (err) {
        alert('复制失败，请手动选择复制');
    }
});

// 参与者：提交签到码
document.getElementById('checkinBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    const input = document.getElementById('checkinCodeInput');
    const code = input.value.trim();
    if (!code) {
        alert('请输入签到码');
        return;
    }
    const btn = document.getElementById('checkinBtn');
    btn.disabled = true;
    btn.textContent = '签到中...';
    try {
        const data = await apiFetch('/api/activities/' + currentActivity.id + '/checkin', {
            method: 'POST',
            body: JSON.stringify({ code })
        });
        if (data.success) {
            alert('签到成功！现在可以为活动评分了。');
            input.value = '';
            selectActivity(currentActivity.id);
        } else {
            alert(data.message || '签到失败');
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    }
    btn.disabled = false;
    btn.textContent = '确认签到';
});

// ==================== 关闭详情 ====================
document.getElementById('detailClose').addEventListener('click', () => {
    document.getElementById('detailEmpty').hidden = false;
    document.getElementById('detailContent').hidden = true;
    const panel = document.getElementById('detailPanel');
    panel.classList.add('empty');
    panel.classList.remove('open');
    currentActivity = null;
    document.querySelectorAll('.activity-card').forEach(card => card.classList.remove('selected'));
});

// ==================== 加入 / 移出「我的」日历 ====================
document.getElementById('calendarBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    const btn = document.getElementById('calendarBtn');
    if (btn.disabled) return;
    btn.disabled = true;

    try {
        const data = await apiFetch(`/api/activities/${currentActivity.id}/calendar`, { method: 'POST' });
        if (data.success) {
            currentActivity.inCalendar = data.inCalendar;
            renderCalendarBtn(currentActivity);
            if (data.inCalendar) {
                alert(`已加入「我的」日历：\n${currentActivity.date} ${currentActivity.time}\n${currentActivity.school} ${currentActivity.location}`);
            } else {
                alert('已从「我的」日历移出');
            }
        } else {
            alert(data.message || '操作失败');
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    } finally {
        btn.disabled = false;
    }
});

// ==================== 报名 ====================
document.getElementById('signupBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    if (document.getElementById('signupBtn').disabled) return;   // 防重复提交
    const hours = currentActivity.creditHours || 0;
    const feeText = hours > 0 ? `学时奖励：${hours} 学时` : '本活动暂无学时奖励';
    let message = `确认报名此活动？\n${feeText}\n\n报名后需现场签到，签到后可评分。`;

    // 报名前的时间冲突提醒（直接使用详情加载时预取的结果）
    if (currentConflicts.length) {
        message += '\n\n【时间冲突提醒】以下已报名活动与该活动时间相近：\n' +
            currentConflicts.map(c => `${c.date} ${c.time}${c.endTime ? '-' + c.endTime : ''} ${c.name}`).join('\n');
    }

    if (!confirm(message)) return;

    const btn = document.getElementById('signupBtn');
    btn.disabled = true;
    btn.textContent = '报名中...';

    try {
        const resp = await fetch(`/api/activities/${currentActivity.id}/signup`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            }
        });
        const data = await resp.json();
        if (data.success) {
            if (data.status === 'waitlist') {
                alert(`活动已满员，已加入候补队列，当前第 ${data.position} 位。\n有人取消报名时会自动递补。`);
            } else {
                alert('报名成功！');
            }
            // 刷新详情
            selectActivity(currentActivity.id);
            // 刷新列表
            loadActivities();
        } else {
            alert(data.message || '报名失败');
            btn.disabled = false;
            btn.textContent = '立即报名';
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
        btn.disabled = false;
        btn.textContent = '立即报名';
    }
});

// ==================== 取消报名 / 退出候补 ====================
document.getElementById('cancelSignupBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    const isWaitlist = !!(checkinStatus && checkinStatus.signupStatus === 'waitlist');
    const tip = isWaitlist ? '确定退出候补队列？' : '确定取消报名？名额会立即释放给候补同学。';
    if (!confirm(tip)) return;

    try {
        const data = await apiFetch('/api/activities/' + currentActivity.id + '/cancel', { method: 'POST' });
        if (data.success) {
            if (data.promoted) {
                alert('已取消报名，候补用户 ' + data.promoted + ' 已自动递补。');
            } else {
                alert(isWaitlist ? '已退出候补队列。' : '已取消报名。');
            }
            selectActivity(currentActivity.id);
            loadActivities();
        } else {
            alert(data.message || '操作失败');
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    }
});

// ==================== 评分 ====================
const ratingStars = document.querySelectorAll('.rate-star');

ratingStars.forEach(star => {
    star.addEventListener('click', () => {
        selectedRating = parseInt(star.dataset.value);
        updateStars();
    });

    star.addEventListener('mouseenter', () => {
        const hoverVal = parseInt(star.dataset.value);
        ratingStars.forEach(s => {
            s.style.color = parseInt(s.dataset.value) <= hoverVal ? '#f59e0b' : 'var(--gray-300)';
        });
    });
});

document.getElementById('ratingStars').addEventListener('mouseleave', () => {
    updateStars();
});

function updateStars() {
    ratingStars.forEach(s => {
        const val = parseInt(s.dataset.value);
        s.style.color = val <= selectedRating ? '#f59e0b' : 'var(--gray-300)';
        s.classList.toggle('active', val <= selectedRating);
    });
}

document.getElementById('submitRating').addEventListener('click', async () => {
    if (!currentActivity) return;
    if (selectedRating === 0) {
        alert('请先选择评分星级');
        return;
    }
    const comment = document.getElementById('ratingComment').value;

    try {
        const resp = await fetch(`/api/activities/${currentActivity.id}/rate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({ stars: selectedRating, comment })
        });
        const data = await resp.json();
        if (data.success) {
            alert('评分提交成功！');
            // 刷新详情
            selectActivity(currentActivity.id);
        } else {
            alert(data.message || '评分失败');
        }
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    }
});

// ==================== 分类筛选 ====================
const catBtns = document.querySelectorAll('.cat-btn');
catBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        catBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const cat = btn.dataset.cat;
        const keyword = document.getElementById('searchInput').value.trim();
        loadActivities(keyword, cat);
    });
});

// ==================== 搜索 ====================
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');

function doSearch() {
    const keyword = searchInput.value.trim();
    const activeCat = document.querySelector('.cat-btn.active');
    const cat = activeCat ? activeCat.dataset.cat : 'all';
    loadActivities(keyword, cat);
}

searchBtn.addEventListener('click', doSearch);
searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') doSearch();
});

// ==================== 智能推荐 ====================
async function loadRecommend() {
    try {
        const data = await apiFetch('/api/recommend');
        if (!data.success) return;
        const list = data.activities || [];
        if (!list.length) return;

        const section = document.getElementById('recommendSection');
        const grid = document.getElementById('recommendGrid');
        const tag = document.getElementById('recommendTag');
        if (data.category) {
            tag.textContent = '根据你对「' + (categoryMap[data.category] || data.category) + '」的兴趣推荐';
        } else {
            tag.textContent = '最新活动推荐';
        }
        grid.innerHTML = list.map(a => renderCard(a, false)).join('');
        section.style.display = '';
        bindCardEvents();
    } catch (err) {
        console.error('推荐加载失败', err);
    }
}

// ==================== 初始化 ====================
(async function init() {
    await loadFavorites();   // 先拿到收藏状态，渲染卡片时心形才正确
    loadActivities();
    loadRecommend();         // 个性化推荐
})();
