// 登录页交互逻辑

const pwd = document.getElementById('password');
const pwdErr = document.getElementById('pwdErr');
const account = document.getElementById('account');
const loginBtn = document.getElementById('loginBtn');
const loginForm = document.getElementById('loginForm');

// 密码输入校验
pwd.addEventListener('input', () => {
    pwdErr.hidden = pwd.value.length > 0;
    toggleLoginBtn();
});

account.addEventListener('input', toggleLoginBtn);

// 账号和密码都填写后才能点击登录
function toggleLoginBtn() {
    loginBtn.disabled = !(account.value.trim().length > 0 && pwd.value.length > 0);
}
toggleLoginBtn();

// 错误提示（替代alert）
function showError(msg) {
    let errEl = document.getElementById('loginErrorMsg');
    if (!errEl) {
        errEl = document.createElement('div');
        errEl.id = 'loginErrorMsg';
        errEl.style.cssText = 'color:#ef4444;font-size:14px;text-align:center;margin-top:12px;padding:8px;background:#fee2e2;border-radius:6px;';
        loginBtn.parentNode.insertBefore(errEl, loginBtn.nextSibling);
    }
    errEl.textContent = msg;
    errEl.style.display = 'block';
    setTimeout(() => { errEl.style.display = 'none'; }, 3000);
}

// 登录表单提交 - 调用后端API，成功后平滑跳转
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (loginBtn.disabled) return;

    // 隐藏之前的错误
    const errEl = document.getElementById('loginErrorMsg');
    if (errEl) errEl.style.display = 'none';

    loginBtn.disabled = true;
    loginBtn.textContent = '登录中...';

    try {
        const resp = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                account: account.value.trim(),
                password: pwd.value
            })
        });

        const data = await resp.json();

        if (data.success) {
            // 保存登录状态
            localStorage.setItem('campus_logged_in', 'true');
            localStorage.setItem('campus_account', account.value.trim());
            localStorage.setItem('campus_token', data.token);
            localStorage.setItem('campus_user', JSON.stringify(data.user));

            // 平滑过渡：淡出当前页面后跳转
            loginBtn.textContent = '登录成功';
            loginBtn.style.background = 'var(--success)';

            document.body.classList.add('page-leaving');

            // 等淡出动画完成后跳转
            setTimeout(() => {
                window.location.replace('../html/home.html');
            }, 400);
            return; // 不执行finally中的重置
        } else {
            showError(data.message || '登录失败');
        }
    } catch (err) {
        showError('网络错误，请检查服务器是否启动');
    }

    // 只有失败时才重置按钮
    loginBtn.disabled = false;
    loginBtn.textContent = '登录';
    toggleLoginBtn();
});
