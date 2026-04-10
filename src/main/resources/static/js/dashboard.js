/**
 * WindWatch AI-SCADA - Dashboard JavaScript
 * Real-time turbine monitoring with WebSocket + Apache ECharts
 */

// ---- State ----
// 터빈 ID는 서버 렌더링 DOM에서 동적으로 감지 (터빈 수 변동 대응)
const turbineIds = Array.from(document.querySelectorAll('[id^="card-"]'))
    .map(el => el.id.replace('card-', ''));
const chartData = { times: [], powerAll: [], windAll: [] };
const turbineChartData = {};
const turbineLatestData = {};
let realtimeChart = null;
let alarmDonutChart = null;
const gaugeCharts = {};
let selectedChartTurbine = 'ALL';
let stompClient = null;

// Per-turbine history for chart
turbineIds.forEach(id => {
    turbineChartData[id] = { power: [], wind: [], times: [] };
});

// ---- Initialize Charts ----
function initRealtimeChart() {
    const dom = document.getElementById('realtimeChart');
    if (!dom) return;
    realtimeChart = echarts.init(dom, 'dark');
    realtimeChart.setOption({
        backgroundColor: 'transparent',
        animation: false,
        grid: { top: 40, bottom: 40, left: 60, right: 80 },
        legend: {
            data: ['발전량 (kW)', '풍속 (m/s)'],
            top: 5,
            textStyle: { color: '#8b949e', fontSize: 12 }
        },
        tooltip: {
            trigger: 'axis',
            backgroundColor: '#161b22',
            borderColor: '#30363d',
            textStyle: { color: '#e6edf3', fontSize: 12 },
            axisPointer: { type: 'cross', lineStyle: { color: '#30363d' } }
        },
        xAxis: {
            type: 'category',
            data: [],
            axisLine: { lineStyle: { color: '#30363d' } },
            axisLabel: { color: '#7d8590', fontSize: 10 },
            boundaryGap: false
        },
        yAxis: [
            {
                name: 'kW',
                type: 'value',
                nameTextStyle: { color: '#7d8590' },
                axisLine: { lineStyle: { color: '#30363d' } },
                axisLabel: { color: '#7d8590', fontSize: 10 },
                splitLine: { lineStyle: { color: '#21262d' } }
            },
            {
                name: 'm/s',
                type: 'value',
                nameTextStyle: { color: '#7d8590' },
                axisLine: { lineStyle: { color: '#30363d' } },
                axisLabel: { color: '#7d8590', fontSize: 10 },
                splitLine: { show: false }
            }
        ],
        series: [
            {
                name: '발전량 (kW)',
                type: 'line',
                data: [],
                smooth: true,
                yAxisIndex: 0,
                lineStyle: { color: '#1f6feb', width: 2 },
                areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(31,111,235,0.3)' }, { offset: 1, color: 'rgba(31,111,235,0.02)' }] } },
                itemStyle: { color: '#1f6feb' },
                symbol: 'none'
            },
            {
                name: '풍속 (m/s)',
                type: 'line',
                data: [],
                smooth: true,
                yAxisIndex: 1,
                lineStyle: { color: '#3fb950', width: 2 },
                itemStyle: { color: '#3fb950' },
                symbol: 'none'
            }
        ]
    });
    window.addEventListener('resize', () => realtimeChart.resize());
}

function initAlarmDonutChart() {
    const dom = document.getElementById('alarmDonutChart');
    if (!dom) return;
    alarmDonutChart = echarts.init(dom, 'dark');
    alarmDonutChart.setOption({
        backgroundColor: 'transparent',
        animation: true,
        tooltip: {
            trigger: 'item',
            backgroundColor: '#161b22',
            borderColor: '#30363d',
            textStyle: { color: '#e6edf3' }
        },
        series: [{
            name: '알람 현황',
            type: 'pie',
            radius: ['50%', '75%'],
            center: ['50%', '50%'],
            data: [
                { value: 0, name: '긴급', itemStyle: { color: '#da3633' } },
                { value: 0, name: '경고', itemStyle: { color: '#d29922' } },
                { value: 0, name: '정보', itemStyle: { color: '#1f6feb' } }
            ],
            label: { show: false },
            emphasis: { label: { show: true, fontSize: 12, color: '#e6edf3' } }
        }]
    });
    window.addEventListener('resize', () => alarmDonutChart.resize());
}

function initGaugeChart(turbineId) {
    const dom = document.getElementById('gauge-' + turbineId);
    if (!dom) return;
    const chart = echarts.init(dom, 'dark');
    chart.setOption({
        backgroundColor: 'transparent',
        animation: false,
        series: [{
            type: 'gauge',
            radius: '90%',
            startAngle: 200,
            endAngle: -20,
            min: 0,
            max: 2000,
            splitNumber: 4,
            progress: {
                show: true,
                width: 8,
                itemStyle: { color: '#1f6feb' }
            },
            axisLine: {
                lineStyle: { width: 8, color: [[1, '#21262d']] }
            },
            axisTick: { show: false },
            splitLine: { show: false },
            axisLabel: { show: false },
            pointer: { show: false },
            detail: {
                valueAnimation: true,
                formatter: '{value}',
                color: '#e6edf3',
                fontSize: 14,
                fontWeight: 700,
                offsetCenter: [0, '10%']
            },
            title: {
                offsetCenter: [0, '40%'],
                fontSize: 10,
                color: '#7d8590'
            },
            data: [{ value: 0, name: 'kW' }]
        }]
    });
    gaugeCharts[turbineId] = chart;
    window.addEventListener('resize', () => chart.resize());
}

// ---- Update Charts with New Data ----
function updateRealtimeChart(batch) {
    if (!realtimeChart) return;
    const now = new Date().toLocaleTimeString('ko-KR');

    if (selectedChartTurbine === 'ALL') {
        const totalPower = batch.reduce((sum, t) => sum + (t.powerOutput || 0), 0);
        const avgWind = batch.reduce((sum, t) => sum + (t.windSpeed || 0), 0) / batch.length;

        chartData.times.push(now);
        chartData.powerAll.push(Math.round(totalPower));
        chartData.windAll.push(Math.round(avgWind * 10) / 10);

        if (chartData.times.length > 60) {
            chartData.times.shift();
            chartData.powerAll.shift();
            chartData.windAll.shift();
        }

        realtimeChart.setOption({
            xAxis: { data: chartData.times },
            series: [
                { data: chartData.powerAll },
                { data: chartData.windAll }
            ]
        });
    } else {
        const turbine = batch.find(t => t.turbineId === selectedChartTurbine);
        if (!turbine) return;
        const td = turbineChartData[selectedChartTurbine];
        td.power.push(Math.round(turbine.powerOutput || 0));
        td.wind.push(Math.round((turbine.windSpeed || 0) * 10) / 10);
        if (!td.times) td.times = [];
        td.times.push(now);
        if (td.times.length > 60) { td.times.shift(); td.power.shift(); td.wind.shift(); }
        realtimeChart.setOption({
            xAxis: { data: td.times },
            series: [{ data: td.power }, { data: td.wind }]
        });
    }
}

function changeChartTurbine(value) {
    selectedChartTurbine = value;
    if (!realtimeChart) return;
    const td = turbineChartData[value];
    if (value !== 'ALL' && td && td.times.length > 0) {
        realtimeChart.setOption({ xAxis: { data: td.times }, series: [{ data: td.power }, { data: td.wind }] });
    } else if (value === 'ALL' && chartData.times.length > 0) {
        realtimeChart.setOption({ xAxis: { data: chartData.times }, series: [{ data: chartData.powerAll }, { data: chartData.windAll }] });
    } else {
        realtimeChart.setOption({ xAxis: { data: [] }, series: [{ data: [] }, { data: [] }] });
    }
}

// ---- Load History on Page Load ----
async function loadChartHistory() {
    if (turbineIds.length === 0) return;
    try {
        const results = await Promise.all(
            turbineIds.map(id =>
                fetch('/api/turbine/' + id + '/history')
                    .then(r => r.json())
                    .then(data => ({ id, data: data.slice(0, 60).reverse() }))
            )
        );

        // Pre-populate per-turbine data
        results.forEach(({ id, data }) => {
            const td = turbineChartData[id];
            data.forEach(record => {
                td.times.push(new Date(record.recordedAt).toLocaleTimeString('ko-KR'));
                td.power.push(Math.round(record.powerOutput || 0));
                td.wind.push(Math.round((record.windSpeed || 0) * 10) / 10);
            });
        });

        // Pre-populate ALL aggregated data (aligned by index — same simulator tick)
        const first = results[0]?.data || [];
        first.forEach((record, i) => {
            chartData.times.push(new Date(record.recordedAt).toLocaleTimeString('ko-KR'));
            const totalPower = results.reduce((sum, { data }) => sum + (data[i]?.powerOutput || 0), 0);
            const avgWind = results.reduce((sum, { data }) => sum + (data[i]?.windSpeed || 0), 0) / results.length;
            chartData.powerAll.push(Math.round(totalPower));
            chartData.windAll.push(Math.round(avgWind * 10) / 10);
        });

        // Render loaded history
        if (realtimeChart && chartData.times.length > 0) {
            realtimeChart.setOption({
                xAxis: { data: chartData.times },
                series: [{ data: chartData.powerAll }, { data: chartData.windAll }]
            });
        }
    } catch (e) {
        console.warn('[WindWatch] Failed to load chart history', e);
    }
}

function updateTurbineCards(batch) {
    let normalCount = 0;
    let criticalCount = 0;
    let highCount = 0;
    let infoCount = 0;
    let totalPower = 0;
    let totalTemp = 0;

    batch.forEach(turbine => {
        turbineLatestData[turbine.turbineId] = turbine;
        const id = turbine.turbineId;
        const card = document.getElementById('card-' + id);
        if (!card) return;

        totalPower += turbine.powerOutput || 0;
        totalTemp += turbine.gearboxTemp || 0;

        // Update card status class
        card.classList.remove('status-critical', 'status-warning', 'status-normal', 'status-stopped');
        if (turbine.status === 'CRITICAL') { card.classList.add('status-critical'); criticalCount++; }
        else if (turbine.status === 'WARNING') { card.classList.add('status-warning'); highCount++; }
        else if (turbine.status === 'STOPPED') { card.classList.add('status-stopped'); }
        else { card.classList.add('status-normal'); normalCount++; }

        // Update status badge
        const statusBadge = document.getElementById('status-' + id);
        if (statusBadge) {
            const statusLabels = { NORMAL: '정상', WARNING: '경고', CRITICAL: '긴급', STOPPED: '정지' };
            statusBadge.textContent = statusLabels[turbine.status] || turbine.status;
            statusBadge.className = 'ww-status-badge';
            if (turbine.status === 'CRITICAL') statusBadge.classList.add('bg-danger', 'text-white');
            else if (turbine.status === 'WARNING') statusBadge.classList.add('bg-warning', 'text-dark');
            else if (turbine.status === 'STOPPED') statusBadge.classList.add('bg-secondary', 'text-white');
            else statusBadge.classList.add('bg-success', 'text-white');
        }

        // Update metrics
        setText('wind-' + id, (turbine.windSpeed || 0).toFixed(1) + ' m/s');
        setText('power-' + id, Math.round(turbine.powerOutput || 0) + ' kW');
        setText('temp-' + id, (turbine.gearboxTemp || 0).toFixed(1) + '°C');
        setText('rpm-' + id, (turbine.rotorRpm || 0).toFixed(1));

        // Update gauge
        if (gaugeCharts[id]) {
            const power = Math.round(turbine.powerOutput || 0);
            const color = turbine.status === 'CRITICAL' ? '#da3633' :
                          turbine.status === 'WARNING' ? '#d29922' : '#1f6feb';
            gaugeCharts[id].setOption({
                series: [{
                    progress: { itemStyle: { color } },
                    data: [{ value: power, name: 'kW' }]
                }]
            });
        }
    });

    // Update KPIs
    setText('kpiTotalPower', Math.round(totalPower).toLocaleString());
    setText('kpiAvgTemp', (totalTemp / batch.length).toFixed(1));
    const stoppedCount = batch.filter(t => t.status === 'STOPPED').length;
    setText('kpiNormalCount', normalCount + '/' + batch.length + ' 정상' + (stoppedCount > 0 ? ' (' + stoppedCount + ' 정지)' : ''));

    // Update alarm donut
    if (alarmDonutChart) {
        alarmDonutChart.setOption({
            series: [{
                data: [
                    { value: criticalCount, name: '긴급', itemStyle: { color: '#da3633' } },
                    { value: highCount, name: '경고', itemStyle: { color: '#d29922' } },
                    { value: infoCount, name: '정보', itemStyle: { color: '#1f6feb' } }
                ]
            }]
        });
    }

    setText('alarmCriticalCount', criticalCount);
    setText('alarmHighCount', highCount);
    setText('alarmInfoCount', infoCount);

    // Update last update time
    setText('lastUpdateTime', new Date().toLocaleTimeString('ko-KR'));

    // 모달이 열려있으면 실시간 갱신
    if (modalActiveTurbineId) {
        const modalData = turbineLatestData[modalActiveTurbineId];
        if (modalData) renderModalData(modalData);
    }
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

// ---- WebSocket Connection ----
function connectWebSocket() {
    window._globalAlarmWsInit = true;
    const socket = new SockJS('/ws');
    stompClient = new StompJs.Client({
        webSocketFactory: () => socket,
        reconnectDelay: 3000,
        onConnect: function () {
            console.log('[WindWatch] WebSocket connected');
            stompClient.subscribe('/topic/turbine-data', function (message) {
                try {
                    const batch = JSON.parse(message.body);
                    updateRealtimeChart(batch);
                    updateTurbineCards(batch);
                } catch (e) {
                    console.error('[WindWatch] Failed to parse turbine data', e);
                }
            });
            stompClient.subscribe('/topic/alerts', function(msg) {
                try {
                    const alert = JSON.parse(msg.body);
                    showToast(
                        '<strong>[' + alert.turbineId + ']</strong> ' + alert.message +
                        ' (' + (alert.value ? parseFloat(alert.value).toFixed(1) : '--') + '°C)',
                        'danger', 6000
                    );
                    const badge = document.getElementById('topbarAlarmCount');
                    if (badge) badge.textContent = parseInt(badge.textContent || '0') + 1;
                } catch(e) {}
            });
        },
        onDisconnect: function () {
            console.warn('[WindWatch] WebSocket disconnected');
        },
        onStompError: function (frame) {
            console.error('[WindWatch] STOMP error', frame);
        }
    });
    stompClient.activate();
}

// ---- Turbine Detail Modal ----
let modalActiveTurbineId = null;
let turbineDetailModal = null;

function renderModalData(data) {
    if (!data) return;
    const statusKor = { NORMAL: '정상', WARNING: '경고', CRITICAL: '긴급', STOPPED: '정지 (저/고풍속)' };
    const temp = data.gearboxTemp || 0;

    document.getElementById('modal-windSpeed').textContent = (data.windSpeed || 0).toFixed(1) + ' m/s';
    document.getElementById('modal-powerOutput').textContent = Math.round(data.powerOutput || 0) + ' kW';
    document.getElementById('modal-rotorRpm').textContent = (data.rotorRpm || 0).toFixed(1) + ' RPM';

    const tempEl = document.getElementById('modal-gearboxTemp');
    tempEl.textContent = temp.toFixed(1) + ' °C';
    tempEl.className = 'fs-4 fw-bold ' + (temp > 85 ? 'text-danger' : temp > 75 ? 'text-warning' : 'text-success');

    document.getElementById('modal-vibration').textContent = (data.vibration || 0).toFixed(2) + ' mm/s';
    document.getElementById('modal-pitchAngle').textContent = (data.pitchAngle || 0).toFixed(1) + ' °';

    const statusEl = document.getElementById('modal-status');
    statusEl.textContent = statusKor[data.status] || data.status || '--';
    statusEl.className = 'badge ' + (
        data.status === 'CRITICAL' ? 'bg-danger' :
        data.status === 'WARNING'  ? 'bg-warning text-dark' :
        data.status === 'STOPPED'  ? 'bg-secondary' : 'bg-success'
    );

    document.getElementById('modal-updatedAt').textContent = new Date().toLocaleTimeString('ko-KR');
}

function openTurbineDetail(turbineId) {
    const data = turbineLatestData[turbineId];
    if (!data) { showToast('아직 데이터를 수신 중입니다. 잠시 후 다시 시도하세요.', 'info'); return; }

    modalActiveTurbineId = turbineId;
    document.getElementById('modalTurbineId').textContent = turbineId + ' 상세 정보';
    renderModalData(data);

    if (!turbineDetailModal) {
        const el = document.getElementById('turbineDetailModal');
        turbineDetailModal = new bootstrap.Modal(el);
        el.addEventListener('hidden.bs.modal', () => { modalActiveTurbineId = null; });
    }
    turbineDetailModal.show();
}

// ---- Init ----
document.addEventListener('DOMContentLoaded', function () {
    initRealtimeChart();
    initAlarmDonutChart();
    turbineIds.forEach(id => initGaugeChart(id));
    loadChartHistory();
    connectWebSocket();

    // Update topbar alarm count from active alarms table
    const alarmBadge = document.getElementById('topbarAlarmCount');
    if (alarmBadge) {
        alarmBadge.textContent = document.querySelectorAll('tbody tr.ww-row-critical, tbody tr.ww-row-warning').length;
    }
});

// ---- Modal → AI Pages ----
function goToInspection() {
    if (!modalActiveTurbineId) return;
    const data = turbineLatestData[modalActiveTurbineId];
    if (data) {
        sessionStorage.setItem('ww_inspection_context', JSON.stringify({
            turbineId: modalActiveTurbineId,
            status: data.status,
            gearboxTemp: data.gearboxTemp,
            vibration: data.vibration,
            powerOutput: data.powerOutput,
            windSpeed: data.windSpeed,
            rotorRpm: data.rotorRpm
        }));
    }
    if (turbineDetailModal) turbineDetailModal.hide();
    window.location.href = '/inspection';
}

function goToAssistant() {
    if (!modalActiveTurbineId) return;
    const data = turbineLatestData[modalActiveTurbineId];
    const question = buildAssistantQuestion(modalActiveTurbineId, data);
    sessionStorage.setItem('ww_assistant_prefill', JSON.stringify({
        turbineId: modalActiveTurbineId,
        question
    }));
    if (turbineDetailModal) turbineDetailModal.hide();
    window.location.href = '/assistant';
}

function buildAssistantQuestion(turbineId, data) {
    if (!data) return `${turbineId} 터빈 상태 분석을 요청합니다.`;
    const statusKor = { NORMAL: '정상', WARNING: '경고', CRITICAL: '긴급', STOPPED: '정지 (저/고풍속)' };
    const tempWarn = data.gearboxTemp > 85 ? ' ⚠️ 임계치(85°C) 초과' : data.gearboxTemp > 75 ? ' ⚠️ 주의 구간(75~85°C)' : '';
    const vibWarn  = data.vibration > 5   ? ' ⚠️ 임계치(5.0 mm/s) 초과' : data.vibration > 3.5 ? ' ⚠️ 주의 구간(3.5~5.0 mm/s)' : '';
    return `[${turbineId} 터빈] 현재 상태 분석 요청

• 운전 상태: ${statusKor[data.status] || data.status}
• 기어박스 온도: ${(data.gearboxTemp || 0).toFixed(1)}°C${tempWarn}
• 진동: ${(data.vibration || 0).toFixed(2)} mm/s${vibWarn}
• 발전량: ${Math.round(data.powerOutput || 0)} kW
• 풍속: ${(data.windSpeed || 0).toFixed(1)} m/s
• 회전속도: ${(data.rotorRpm || 0).toFixed(1)} RPM

위 센서 데이터를 바탕으로 이상 원인 분석 및 권장 조치 사항을 알려주세요.`;
}
