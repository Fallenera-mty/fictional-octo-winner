// 注册页交互逻辑：实时表单校验 + 调用后端API

const pwd = document.getElementById('password');
const pwdErr = document.getElementById('pwdErr');
const accountInput = document.getElementById('account');
const accountErr = document.getElementById('accountErr');
const phoneInput = document.getElementById('phone');
const phoneErr = document.getElementById('phoneErr');
const strengthBox = document.getElementById('pwdStrength');
const strengthFill = document.getElementById('strengthFill');
const strengthText = document.getElementById('strengthText');

// ==================== 实时校验 ====================

// 1. 账号格式：4~16位字母、数字或下划线
accountInput.addEventListener('input', () => {
    const ok = /^[A-Za-z0-9_]{4,16}$/.test(accountInput.value.trim());
    accountErr.hidden = ok || accountInput.value.length === 0;
});

// 2. 密码长度 + 强度实时提示
pwd.addEventListener('input', () => {
    const v = pwd.value;
    pwdErr.hidden = v.length === 0 || (v.length >= 8 && v.length <= 16);

    // 强度评估：长度达标得1分，含字母得1分，含数字得1分，含符号得1分
    let score = 0;
    if (v.length >= 8) score++;
    if (/[A-Za-z]/.test(v)) score++;
    if (/\d/.test(v)) score++;
    if (/[^A-Za-z0-9]/.test(v)) score++;

    if (v.length === 0) {
        strengthBox.hidden = true;
    } else {
        strengthBox.hidden = false;
        const levels = [
            { text: '弱', color: '#ef4444', width: '25%' },
            { text: '弱', color: '#ef4444', width: '25%' },
            { text: '中', color: '#f59e0b', width: '60%' },
            { text: '强', color: '#22c55e', width: '100%' },
            { text: '很强', color: '#22c55e', width: '100%' }
        ];
        const lv = levels[score];
        strengthText.textContent = '密码强度：' + lv.text;
        strengthText.style.color = lv.color;
        strengthFill.style.width = lv.width;
        strengthFill.style.background = lv.color;
    }
});

// 3. 手机号格式：11位数字
phoneInput.addEventListener('input', () => {
    const ok = /^1\d{10}$/.test(phoneInput.value.trim());
    phoneErr.hidden = ok || phoneInput.value.length === 0;
});

// ==================== 提交控制 ====================

// 协议勾选控制提交按钮
const agree = document.getElementById('agree');
const submitBtn = document.getElementById('submitBtn');

function toggleSubmit() {
    submitBtn.disabled = !agree.checked;
}

agree.addEventListener('change', toggleSubmit);
toggleSubmit();

// 提交前整体校验
function validateAll() {
    let ok = true;

    if (!/^[A-Za-z0-9_]{4,16}$/.test(accountInput.value.trim())) {
        accountErr.hidden = false;
        ok = false;
    }
    if (!(pwd.value.length >= 8 && pwd.value.length <= 16)) {
        pwdErr.hidden = false;
        ok = false;
    }
    if (!/^1\d{10}$/.test(phoneInput.value.trim())) {
        phoneErr.hidden = false;
        ok = false;
    }
    return ok;
}

// ==================== 注册提交 ====================
document.getElementById('registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitBtn.disabled) return;
    if (!validateAll()) return;

    submitBtn.disabled = true;
    submitBtn.textContent = '注册中...';

    try {
        const resp = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                account: accountInput.value.trim(),
                password: pwd.value,
                nickname: document.getElementById('nickname').value.trim(),
                school: document.getElementById('school').value.trim(),
                phone: phoneInput.value.trim()
            })
        });

        const data = await resp.json();

        if (data.success) {
            alert('注册成功！即将跳转到登录页...');
            window.location.href = '../html/login.html';
        } else {
            alert(data.message || '注册失败');
        }
    } catch (err) {
        alert('网络错误，请检查服务器是否启动');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = '注册';
        toggleSubmit();
    }
});