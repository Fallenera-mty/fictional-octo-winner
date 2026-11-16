// 首页交互逻辑

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

// 4. 分类筛选
const catBtns = document.querySelectorAll('.cat-btn');
const activityCards = document.querySelectorAll('.activity-grid .activity-card');

catBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        // 切换激活状态
        catBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        const cat = btn.dataset.cat;

        activityCards.forEach(card => {
            if (cat === 'all' || card.dataset.cat === cat) {
                card.style.display = '';
            } else {
                card.style.display = 'none';
            }
        });
    });
});

// 5. 搜索功能
const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');

function doSearch() {
    const keyword = searchInput.value.trim().toLowerCase();
    activityCards.forEach(card => {
        const text = card.textContent.toLowerCase();
        card.style.display = text.includes(keyword) ? '' : 'none';
    });
}

searchBtn.addEventListener('click', doSearch);
searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') doSearch();
});