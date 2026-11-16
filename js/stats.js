// 数据大屏交互逻辑 - 统计卡片 + ECharts 图表
// 公共部分（登录检查、用户区、时钟、退出登录）在 common.js 中

guardLogin();
initUserArea();
initClock();

const categoryMap = {
    lecture: '讲座论坛', sports: '体育竞技', art: '文艺演出',
    tech: '科技创新', volunteer: '志愿公益', social: '社交聚会'
};

const vipNameMap = {
    free: '普通用户', campus: '校园会员', premium: '高级会员'
};

function renderFallback(elId, data) {
    const el = document.getElementById(elId);
    el.hidden = false;
    el.innerHTML = Object.keys(data).map(k => {
        const name = categoryMap[k] || vipNameMap[k] || k;
        return `<span class="tag tag-primary">${name}: ${data[k]}</span>`;
    }).join(' ');
}

function initCategoryChart(categoryCount) {
    const box = document.getElementById('categoryChart');
    if (typeof echarts === 'undefined') {
        box.style.display = 'none';
        renderFallback('categoryFallback', categoryCount);
        return;
    }
    const chart = echarts.init(box);
    const names = Object.keys(categoryCount).map(k => categoryMap[k] || k);
    const values = Object.keys(categoryCount).map(k => categoryCount[k]);
    chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 50, right: 20, top: 30, bottom: 40 },
        xAxis: {
            type: 'category',
            data: names,
            axisLabel: { interval: 0, rotate: names.length > 4 ? 20 : 0 }
        },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{
            type: 'bar',
            data: values,
            barMaxWidth: 46,
            itemStyle: {
                color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
                    colorStops: [{ offset: 0, color: '#6366f1' }, { offset: 1, color: '#a5b4fc' }] },
                borderRadius: [6, 6, 0, 0]
            }
        }]
    });
    window.addEventListener('resize', () => chart.resize());
}

function initVipChart(vipCount) {
    const box = document.getElementById('vipChart');
    if (typeof echarts === 'undefined') {
        box.style.display = 'none';
        renderFallback('vipFallback', vipCount);
        return;
    }
    const chart = echarts.init(box);
    const data = Object.keys(vipCount).map(k => ({
        name: vipNameMap[k] || k,
        value: vipCount[k]
    }));
    chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
        legend: { bottom: 0 },
        series: [{
            type: 'pie',
            radius: ['40%', '65%'],
            center: ['50%', '45%'],
            data: data,
            label: { formatter: '{b}\n{d}%' },
            itemStyle: { borderRadius: 6, borderWidth: 2, borderColor: '#fff' }
        }]
    });
    window.addEventListener('resize', () => chart.resize());
}

(async function init() {
    try {
        const resp = await fetch('/api/stats');
        const stats = await resp.json();
        if (!stats.success && stats.totalUsers === undefined) return;

        document.getElementById('statUsers').textContent = stats.totalUsers ?? '-';
        document.getElementById('statActivities').textContent = stats.totalActivities ?? '-';
        document.getElementById('statSignups').textContent = stats.totalSignups ?? '-';
        document.getElementById('statRatings').textContent = stats.totalRatings ?? '-';

        initCategoryChart(stats.categoryCount || {});
        initVipChart(stats.vipCount || {});
    } catch (err) {
        console.error('统计数据加载失败', err);
    }
})();