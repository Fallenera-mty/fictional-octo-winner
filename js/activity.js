// 活动详情页交互逻辑

// 公共部分（登录检查、用户区、退出登录）在 common.js 中
guardLogin();
initUserArea();
initClock();

// 4. 报名功能
const signupBtn = document.getElementById('signupBtn');
let hasSignedUp = false;

signupBtn.addEventListener('click', () => {
    if (hasSignedUp) return;
    if (confirm('确认报名此活动？\n报名费：¥29.9\n\n报名后活动结束可进行评分。')) {
        hasSignedUp = true;
        signupBtn.textContent = '已报名';
        signupBtn.disabled = true;
        signupBtn.style.background = 'var(--success)';

        // 模拟活动已结束，显示评分区域
        setTimeout(() => {
            document.getElementById('myRating').hidden = false;
            document.getElementById('ratingLocked').hidden = true;
        }, 500);
    }
});

// 5. 评分功能 - 星星选择
const ratingStars = document.querySelectorAll('.rate-star');
let selectedRating = 0;

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
        s.style.color = parseInt(s.dataset.value) <= selectedRating ? '#f59e0b' : 'var(--gray-300)';
        if (parseInt(s.dataset.value) <= selectedRating) {
            s.classList.add('active');
        } else {
            s.classList.remove('active');
        }
    });
}

// 6. 提交评分
document.getElementById('submitRating').addEventListener('click', () => {
    if (selectedRating === 0) {
        alert('请先选择评分星级');
        return;
    }
    const comment = document.getElementById('ratingComment').value;
    alert(`评分提交成功！\n\n评分：${selectedRating} 星\n评价：${comment || '无'}\n\n感谢你的反馈，评分将用于监管活动质量。`);

    // 隐藏评分区域，显示已评分
    document.getElementById('myRating').innerHTML = `
        <p style="font-size:14px; color:var(--success); font-weight:600;">已评分：${'★'.repeat(selectedRating)}${'☆'.repeat(5 - selectedRating)}</p>
        ${comment ? `<p style="font-size:13px; color:var(--gray-500); margin-top:6px;">${escapeHtml(comment)}</p>` : ''}
    `;
});
// ==================== 过期活动报名拦截（真实接口校验） ====================
const pageActivityId = new URLSearchParams(window.location.search).get('id') || '1';
(async function checkExpired() {
    try {
        const resp = await fetch('/api/activities/' + pageActivityId);
        const data = await resp.json();
        if (data.success && data.activity) {
            const expired = data.activity.date < new Date().toISOString().slice(0, 10);
            if (expired) {
                signupBtn.textContent = '活动已结束';
                signupBtn.disabled = true;
                signupBtn.style.background = 'var(--gray-300)';
            }
        }
    } catch (err) { /* 校验失败不阻塞页面 */ }
})();

// ==================== 评论区（真实接口） ====================
async function loadComments() {
    const listEl = document.getElementById('commentList');
    try {
        const resp = await fetch('/api/activities/' + pageActivityId + '/comments');
        const data = await resp.json();
        const list = Array.isArray(data) ? data : [];
        if (!list.length) {
            listEl.innerHTML = '<div class="comment-empty">还没有评论，快来抢沙发~</div>';
            return;
        }
        listEl.innerHTML = list.map(c => `
            <div class="comment-item">
                <div class="comment-avatar">${escapeHtml((c.nickname || c.account || 'U').charAt(0))}</div>
                <div class="comment-main">
                    <div class="comment-head">
                        <span class="comment-author">${escapeHtml(c.nickname || c.account)}</span>
                        <span class="comment-time">${formatCommentTime(c.createdAt)}</span>
                    </div>
                    <div class="comment-content">${escapeHtml(c.content)}</div>
                </div>
            </div>
        `).join('');
    } catch (err) {
        listEl.innerHTML = '<div class="comment-empty">评论加载失败，请稍后重试</div>';
    }
}

function formatCommentTime(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    const pad = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

document.getElementById('commentSubmit').addEventListener('click', async () => {
    const input = document.getElementById('commentInput');
    const content = input.value.trim();
    if (!content) {
        alert('评论内容不能为空');
        return;
    }
    const btn = document.getElementById('commentSubmit');
    btn.disabled = true;
    try {
        const resp = await fetch('/api/activities/' + pageActivityId + '/comments', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + localStorage.getItem('campus_token')
            },
            body: JSON.stringify({ content })
        });
        const data = await resp.json();
        if (Array.isArray(data)) {
            input.value = '';
            loadComments();
        } else {
            alert((data && data.message) || '发表失败');
        }
    } catch (err) {
        alert('网络错误，请检查服务器是否启动');
    }
    btn.disabled = false;
});

loadComments();