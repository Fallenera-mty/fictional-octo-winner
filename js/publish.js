// 发布活动页交互逻辑
guardLogin();
initUserArea();
initClock();

// 仅老师/管理员可发布
(async () => {
    try {
        const data = await apiFetch('/api/user');
        const u = data.user || data;
        if (!u || (u.userType !== 'teacher' && u.role !== 'admin')) {
            alert('发布活动仅对老师开放');
            window.location.href = 'home.html';
        }
    } catch (e) {}
})();

const publishForm = document.getElementById('publishForm');
const publishBtn = document.getElementById('publishBtn');
const actDate = document.getElementById('actDate');
const actTime = document.getElementById('actTime');
const actEndTime = document.getElementById('actEndTime');

// 开始时间变化时，自动把结束时间顺延 2 小时（不覆盖更晚的已有值）
function plusHours(hhmm, hours) {
    const parts = hhmm.split(':').map(Number);
    if (parts.length !== 2 || isNaN(parts[0]) || isNaN(parts[1])) return '';
    const total = Math.min(parts[0] * 60 + parts[1] + hours * 60, 23 * 60 + 59);
    return String(Math.floor(total / 60)).padStart(2, '0') + ':' + String(total % 60).padStart(2, '0');
}

actTime.addEventListener('change', () => {
    if (!actTime.value) return;
    if (!actEndTime.value || actEndTime.value <= actTime.value) {
        actEndTime.value = plusHours(actTime.value, 2);
    }
});

// 日期不能早于今天
const today = new Date();
const pad = n => String(n).padStart(2, '0');
actDate.min = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;

publishForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const name = document.getElementById('actName').value.trim();
    const category = document.getElementById('actCategory').value;
    const description = document.getElementById('actDesc').value.trim();
    const date = actDate.value;
    const time = actTime.value || '14:00';
    const endTime = actEndTime.value;
    const location = document.getElementById('actLocation').value.trim();
    const school = document.getElementById('actSchool').value.trim();
    const maxRaw = document.getElementById('actMaxPeople').value.trim();
    const creditHours = parseInt(document.getElementById('actCreditHours').value, 10) || 0;
    const tags = document.getElementById('actTags').value.trim().replace(/，/g, ',');

    if (!name || !category || !description || !date || !location || !school) {
        alert('请填写活动名称、分类、介绍、日期、地点和学校');
        return;
    }
    if (endTime && endTime <= time) {
        alert('结束时间需晚于开始时间');
        return;
    }
    const maxPeople = maxRaw === '' ? 0 : parseInt(maxRaw, 10);
    if (maxRaw !== '' && (!Number.isInteger(maxPeople) || maxPeople < 1)) {
        alert('人数上限需为正整数，或留空表示不限');
        return;
    }
    if (!Number.isInteger(creditHours) || creditHours < 0 || creditHours > 20) {
        alert('学时奖励需在 0~20 之间');
        return;
    }

    publishBtn.disabled = true;
    const originalText = publishBtn.textContent;
    publishBtn.textContent = '发布中...';
    try {
        const data = await apiFetch('/api/activities', {
            method: 'POST',
            body: JSON.stringify({
                name, category, description, date, time, endTime, location, school,
                maxPeople, fee: 0, tags, promoted: false,
                promotePlan: '', promoteDays: 0, creditHours
            })
        });
        if (!data.success) {
            alert(data.message || '发布失败，请稍后重试');
            return;
        }
        alert('发布成功！活动已上线，可在首页查看。');
        const newId = data.activity && data.activity.id;
        window.location.href = newId ? 'home.html?new=' + newId : 'home.html';
    } catch (err) {
        alert('网络错误：连接不到服务器，请确认服务正在运行（http://localhost:8080）');
    } finally {
        publishBtn.disabled = false;
        publishBtn.textContent = originalText;
    }
});
