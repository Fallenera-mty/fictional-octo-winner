// 活动详情页交互逻辑

// 1. 检查登录状态
(function checkLogin() {
    const loggedIn = localStorage.getItem('campus_logged_in');
    if (!loggedIn) {
        window.location.href = '../html/login.html';
    }
})();

// 2. 显示用户信息
const accountSpan = document.getElementById('userAccount');
const account = localStorage.getItem('campus_account');
accountSpan.textContent = account || '用户';

// 3. 退出登录
document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('campus_logged_in');
    localStorage.removeItem('campus_account');
    window.location.href = '../html/login.html';
});

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
        ${comment ? `<p style="font-size:13px; color:var(--gray-500); margin-top:6px;">${comment}</p>` : ''}
    `;
});