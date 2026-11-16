// 发布活动页交互逻辑

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

// 4. 报名费类型切换
const feeRadios = document.querySelectorAll('input[name="feeType"]');
const feeInput = document.getElementById('feeInput');
const actFee = document.getElementById('actFee');
const feePreview = document.getElementById('feePreview');

feeRadios.forEach(radio => {
    radio.addEventListener('change', () => {
        const isPaid = radio.value === 'paid';
        feeInput.hidden = !isPaid;
        if (!isPaid) {
            actFee.value = '';
            feePreview.hidden = true;
        }
    });
});

// 5. 报名费实时计算
actFee.addEventListener('input', () => {
    const fee = parseFloat(actFee.value);
    if (fee > 0) {
        feePreview.hidden = false;
        const commission = fee * 0.1;
        const net = fee - commission;
        document.getElementById('feeTotal').textContent = `¥${fee.toFixed(2)}`;
        document.getElementById('feeCommission').textContent = `-¥${commission.toFixed(2)}`;
        document.getElementById('feeNet').textContent = `¥${net.toFixed(2)}`;
    } else {
        feePreview.hidden = true;
    }
});

// 6. 推广设置切换
const wantPromote = document.getElementById('wantPromote');
const promoteInfo = document.getElementById('promoteInfo');

wantPromote.addEventListener('change', () => {
    promoteInfo.hidden = !wantPromote.checked;
});

// 7. 表单提交
document.getElementById('publishForm').addEventListener('submit', (e) => {
    e.preventDefault();

    const name = document.getElementById('actName').value;
    const category = document.getElementById('actCategory').value;
    const desc = document.getElementById('actDesc').value;
    const date = document.getElementById('actDate').value;
    const location = document.getElementById('actLocation').value;
    const school = document.getElementById('actSchool').value;

    if (!name || !category || !desc || !date || !location || !school) {
        alert('请填写所有必填项');
        return;
    }

    // 获取报名费信息
    const feeType = document.querySelector('input[name="feeType"]:checked').value;
    const fee = feeType === 'paid' ? parseFloat(actFee.value) : 0;

    if (feeType === 'paid' && (!fee || fee <= 0)) {
        alert('请设置报名费金额');
        return;
    }

    // 获取推广信息
    let promoteInfo = '无';
    if (wantPromote.checked) {
        const plan = document.querySelector('input[name="promotePlan"]:checked').value;
        const days = document.getElementById('promoteDays').value;
        const planNames = { basic: '基础推广', premium: '高级推广', ultimate: '旗舰推广' };
        promoteInfo = `${planNames[plan]}，${days}天`;
    }

    alert(`活动发布成功！\n\n活动名称：${name}\n分类：${category}\n日期：${date}\n地点：${location}\n报名费：${fee > 0 ? '¥' + fee.toFixed(2) : '免费'}\n推广：${promoteInfo}\n\n即将跳转到首页...`);

    window.location.href = '../html/index.html';
});