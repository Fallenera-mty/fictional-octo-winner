// 推广中心页交互逻辑

// 公共部分（登录检查、用户区、退出登录）在 common.js 中
guardLogin();
initUserArea();
initClock();

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

// 会员折扣（默认无折扣，从后端接口获取真实等级后更新）
let discount = 1;

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

// 从后端获取真实会员等级，更新折扣和提示
(async function loadVipDiscount() {
    try {
        const data = await apiFetch('/api/user');
        if (!data.success) return;
        const vipLevel = data.user.vipLevel;

        if (vipLevel === 'premium') {
            discount = 0.8;
            document.getElementById('vipTipText').innerHTML = '你已是<strong>活动达人</strong>会员，推广费用享<strong>8折优惠</strong>！';
        } else if (vipLevel === 'campus') {
            document.getElementById('vipTipText').innerHTML = '升级为<strong>活动达人</strong>会员，推广费用可享<strong>8折优惠</strong>！<a href="vip.html">立即升级 ></a>';
        }

        // 折扣可能变了，重算三个套餐的合计
        Object.keys(prices).forEach(updateTotal);
    } catch (err) {
        // 接口失败时保持无折扣价格
    }
})();

// 5. 购买推广
function buyPromote(plan) {
    const planNames = { basic: '基础推广', premium: '高级推广', ultimate: '旗舰推广' };
    const days = parseInt(daySelects[plan].value);
    const total = prices[plan] * days * discount;

    if (confirm(`确认购买${planNames[plan]}？\n\n天数：${days}天\n单价：¥${prices[plan]}/天${discount < 1 ? '\n会员折扣：8折' : ''}\n合计：¥${total}\n\n购买后活动将在首推位展示。`)) {
        alert('推广购买成功！活动将在首推位展示。');
    }
}