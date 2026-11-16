// 主页面交互逻辑 - 活动列表 + 详情

// ==================== 登录检查 ====================
(function checkLogin() {
    const loggedIn = localStorage.getItem('campus_logged_in');
    if (!loggedIn) {
        window.location.href = '../html/login.html';
    }
})();

const token = localStorage.getItem('campus_token');
const account = localStorage.getItem('campus_account');

// 显示用户信息
document.getElementById('userAccount').textContent = account || '用户';

// 退出登录
document.getElementById('logoutBtn').addEventListener('click', async () => {
    try {
        await fetch('/api/logout', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        });
    } catch (e) {}
    localStorage.removeItem('campus_logged_in');
    localStorage.removeItem('campus_account');
    localStorage.removeItem('campus_token');
    localStorage.removeItem('campus_user');
    window.location.href = '../html/login.html';
});

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

// ==================== 加载活动列表 ====================
async function loadActivities(keyword, category) {
    try {
        let url = '/api/activities';
        const params = [];
        if (keyword) params.push('keyword=' + encodeURIComponent(keyword));
        if (category && category !== 'all') params.push('category=' + encodeURIComponent(category));
        if (params.length) url += '?' + params.join('&');

        const resp = await fetch(url);
        const data = await resp.json();
        allActivities = Array.isArray(data) ? data : [];
        renderActivities();
    } catch (err) {
        console.error('加载活动失败', err);
        allActivities = [];
        renderActivities();
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

    // 绑定点击事件
    document.querySelectorAll('.activity-card').forEach(card => {
        card.addEventListener('click', (e) => {
            e.preventDefault();
            const id = parseInt(card.dataset.id);
            selectActivity(id);
        });
    });
}

function renderCard(act, isPromote) {
    const catName = categoryMap[act.category] || act.category;
    const tagClass = categoryTagClass[act.category] || 'tag-primary';
    const priceText = act.fee > 0 ? `¥${act.fee}` : '免费';
    const priceClass = act.fee > 0 ? 'price' : 'price free';
    const selectedClass = currentActivity && currentActivity.id === act.id ? ' selected' : '';

    return `
        <div class="activity-card${isPromote ? ' promote-card' : ''}${selectedClass}" data-id="${act.id}" data-cat="${act.category}">
            <div class="card-banner" style="background: ${categoryColors[act.category] || categoryColors.tech}">
                ${isPromote ? '<span class="promote-badge">首推</span>' : ''}
                <span class="tag ${tagClass}">${catName}</span>
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

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ==================== 选中活动 - 展示详情 ====================
async function selectActivity(id) {
    try {
        const resp = await fetch(`/api/activities/${id}`);
        const data = await resp.json();
        if (!data.success) return;

        currentActivity = data.activity;
        renderDetail(data);
    } catch (err) {
        console.error('加载活动详情失败', err);
    }
}

function renderDetail(data) {
    const act = data.activity;
    const signupAccounts = data.signupAccounts || [];
    const ratingList = data.ratings || [];

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
        ${act.promoted ? '<span class="promote-badge">首推</span>' : ''}
        <span class="tag ${tagClass}" style="font-size:13px;">${catName}</span>
    `;

    // 标题
    document.getElementById('detailTitle').textContent = act.name;

    // 元信息
    const metaEl = document.getElementById('detailMeta');
    const maxText = act.maxPeople > 0 ? `${signupAccounts.length} / ${act.maxPeople} 人` : `${signupAccounts.length} 人`;
    metaEl.innerHTML = `
        <div class="meta-row"><span class="meta-label">发布者</span><span class="meta-value">${escapeHtml(act.publisher)}</span></div>
        <div class="meta-row"><span class="meta-label">时间</span><span class="meta-value">${act.date} ${act.time}</span></div>
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

    // 价格
    const priceEl = document.getElementById('detailPrice');
    if (act.fee > 0) {
        priceEl.textContent = `¥${act.fee}`;
        priceEl.classList.remove('free');
    } else {
        priceEl.textContent = '免费';
        priceEl.classList.add('free');
    }

    // 报名信息
    const signupInfoEl = document.getElementById('detailSignupInfo');
    if (act.fee > 0) {
        const commission = (act.commission || 0).toFixed(2);
        const net = (act.netIncome || 0).toFixed(2);
        signupInfoEl.innerHTML = `
            <span>平台服务费 ${(act.commissionRate * 100).toFixed(0)}%：¥${commission}</span>
            <span>发布者实得：¥${net}</span>
        `;
    } else {
        signupInfoEl.innerHTML = '<span>免费活动</span>';
    }

    // 报名按钮状态
    const signupBtn = document.getElementById('signupBtn');
    const isSignedUp = signupAccounts.includes(account);
    if (isSignedUp) {
        signupBtn.textContent = '已报名';
        signupBtn.disabled = true;
        signupBtn.style.background = 'var(--success)';
    } else {
        signupBtn.textContent = '立即报名';
        signupBtn.disabled = false;
        signupBtn.style.background = '';
    }

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

    // 评分区域
    const myRatingEl = document.getElementById('myRating');
    const ratingLockedEl = document.getElementById('ratingLocked');
    if (isSignedUp) {
        myRatingEl.hidden = false;
        ratingLockedEl.hidden = true;
    } else {
        myRatingEl.hidden = true;
        ratingLockedEl.hidden = false;
    }
    selectedRating = 0;
    updateStars();

    // 高亮选中的卡片
    document.querySelectorAll('.activity-card').forEach(card => {
        card.classList.toggle('selected', parseInt(card.dataset.id) === act.id);
    });
}

function renderStars(avg) {
    let html = '';
    for (let i = 1; i <= 5; i++) {
        html += i <= Math.round(avg) ? '<span>★</span>' : '<span class="empty">★</span>';
    }
    return html;
}

// ==================== 关闭详情 ====================
document.getElementById('detailClose').addEventListener('click', () => {
    document.getElementById('detailEmpty').hidden = false;
    document.getElementById('detailContent').hidden = true;
    document.getElementById('detailPanel').classList.add('empty');
    currentActivity = null;
    document.querySelectorAll('.activity-card').forEach(card => card.classList.remove('selected'));
});

// ==================== 报名 ====================
document.getElementById('signupBtn').addEventListener('click', async () => {
    if (!currentActivity) return;
    const feeText = currentActivity.fee > 0 ? `报名费：¥${currentActivity.fee}` : '免费活动';
    if (!confirm(`确认报名此活动？\n${feeText}\n\n报名后活动结束可进行评分。`)) return;

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
            alert('报名成功！');
            // 刷新详情
            selectActivity(currentActivity.id);
            // 刷新列表
            loadActivities();
        } else {
            alert(data.message || '报名失败');
        }
    } catch (err) {
        alert('网络错误');
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
        alert('网络错误');
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

// ==================== 初始化 ====================
loadActivities();
