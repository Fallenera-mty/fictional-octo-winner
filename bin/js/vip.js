// 会员中心页交互逻辑

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

// 4. 检查会员状态
(function checkVipStatus() {
    const vipLevel = localStorage.getItem('campus_vip');
    const vipTitle = document.getElementById('vipTitle');
    const vipDesc = document.getElementById('vipDesc');
    const vipExpire = document.getElementById('vipExpire');
    const vipStatus = document.getElementById('vipStatus');

    if (vipLevel === 'campus') {
        vipTitle.textContent = '校园达人会员';
        vipDesc.textContent = '享受发布活动、优先报名等权益';
        vipExpire.hidden = false;
        vipStatus.style.background = 'linear-gradient(135deg, #eef2ff, #e0e7ff)';
        vipStatus.style.borderColor = '#a5b4fc';
    } else if (vipLevel === 'premium') {
        vipTitle.textContent = '活动达人会员';
        vipDesc.textContent = '享受无限发布、佣金减免、专属客服等全部权益';
        vipExpire.hidden = false;
        vipStatus.style.background = 'linear-gradient(135deg, #fef3c7, #fde68a)';
        vipStatus.style.borderColor = '#fbbf24';
    }
})();

// 5. 购买会员
function buyVip(plan) {
    const planNames = {
        campus: '校园达人（¥9.9/月）',
        premium: '活动达人（¥29.9/月）'
    };

    if (confirm(`确认开通${planNames[plan]}？\n\n开通后即可享受对应会员权益。`)) {
        localStorage.setItem('campus_vip', plan);
        alert('会员开通成功！即将刷新页面...');
        location.reload();
    }
}