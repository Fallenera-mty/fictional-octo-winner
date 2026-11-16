// 推广中心页交互逻辑

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

// 4. 推广价格计算
const prices = { basic: 50, premium: 120, ultimate: 200 };
const daySelects = {
    basic: document.getElementById('basicDays'),
    premium: document.getElementById('premiumDays'),
    ultimate: document.getElementById('ultimateDays')
};
const totalEls = {
    basic: document.getElementById('basicTotal'),
    premium: document.getElementById('premiumTotal'),
    ultimate: document.getElementById('ultimateTotal')
};

// 检查会员折扣
const vipLevel = localStorage.getItem('campus_vip');
const discount = vipLevel === 'premium' ? 0.8 : 1;

// 更新会员提示
if (vipLevel === 'premium') {
    document.getElementById('vipTipText').innerHTML = '你已是<strong>活动达人</strong>会员，推广费用享<strong>8折优惠</strong>！';
} else if (vipLevel === 'campus') {
    document.getElementById('vipTipText').innerHTML = '升级为<strong>活动达人</strong>会员，推广费用可享<strong>8折优惠</strong>！<a href="vip.html">立即升级 ></a>';
}

function updateTotal(plan) {
    const days = parseInt(daySelects[plan].value);
    const total = prices[plan] * days * discount;
    totalEls[plan].textContent = `¥${total}`;
}

// 初始化并绑定事件
Object.keys(prices).forEach(plan => {
    updateTotal(plan);
    daySelects[plan].addEventListener('change', () => updateTotal(plan));
});

// 5. 购买推广
function buyPromote(plan) {
    const planNames = { basic: '基础推广', premium: '高级推广', ultimate: '旗舰推广' };
    const days = parseInt(daySelects[plan].value);
    const total = prices[plan] * days * discount;

    if (confirm(`确认购买${planNames[plan]}？\n\n天数：${days}天\n单价：¥${prices[plan]}/天${discount < 1 ? '\n会员折扣：8折' : ''}\n合计：¥${total}\n\n购买后活动将在首推位展示。`)) {
        alert('推广购买成功！活动将在首推位展示。');
    }
}