// 会员中心页交互逻辑

// 公共部分（登录检查、用户区、退出登录）在 common.js 中
guardLogin();
initUserArea();
initClock();

// 4. 检查会员状态（从后端接口获取真实会员等级）
(async function checkVipStatus() {
    const vipTitle = document.getElementById('vipTitle');
    const vipDesc = document.getElementById('vipDesc');
    const vipExpire = document.getElementById('vipExpire');
    const vipStatus = document.getElementById('vipStatus');

    try {
        const data = await apiFetch('/api/user');
        if (!data.success) return;
        const vipLevel = data.user.vipLevel;

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
    } catch (err) {
        // 接口失败时保持页面默认显示
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