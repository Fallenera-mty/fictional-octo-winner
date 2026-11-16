// 注册页交互逻辑

// 1. 密码长度校验
const pwd = document.getElementById('password');
const pwdErr = document.getElementById('pwdErr');
pwd.addEventListener('input', () => {
    const ok = pwd.value.length >= 8 && pwd.value.length <= 16;
    pwdErr.hidden = ok;
});

// 2. 协议勾选控制提交按钮
const agree = document.getElementById('agree');
const submitBtn = document.getElementById('submitBtn');

function toggleSubmit() {
    submitBtn.disabled = !agree.checked;
}

agree.addEventListener('change', toggleSubmit);
toggleSubmit();

// 3. 注册提交 - 调用后端API
document.getElementById('registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitBtn.disabled) return;

    submitBtn.disabled = true;
    submitBtn.textContent = '注册中...';

    try {
        const resp = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                account: document.getElementById('account').value.trim(),
                password: pwd.value,
                nickname: document.getElementById('nickname').value.trim(),
                school: document.getElementById('school').value.trim(),
                phone: document.getElementById('phone').value.trim()
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