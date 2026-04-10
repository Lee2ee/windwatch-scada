/**
 * WindWatch AI-SCADA - Inspection Page JavaScript
 * AI blade defect detection interface
 */

let selectedFile = null;

// ---- Drag & Drop ----
function handleDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    document.getElementById('dropzone').classList.add('dragover');
}

function handleDragLeave(e) {
    e.preventDefault();
    document.getElementById('dropzone').classList.remove('dragover');
}

function handleDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    document.getElementById('dropzone').classList.remove('dragover');
    const files = e.dataTransfer.files;
    if (files.length > 0) {
        processFile(files[0]);
    }
}

function handleFileSelect(e) {
    const file = e.target.files[0];
    if (file) processFile(file);
}

function processFile(file) {
    // Validate
    if (!file.type.startsWith('image/')) {
        showError('이미지 파일만 업로드할 수 있습니다.');
        return;
    }
    if (file.size > 10 * 1024 * 1024) {
        showError('파일 크기는 10MB를 초과할 수 없습니다.');
        return;
    }

    selectedFile = file;

    // Show preview
    const reader = new FileReader();
    reader.onload = function (e) {
        const preview = document.getElementById('previewImage');
        preview.src = e.target.result;

        document.getElementById('dropzoneContent').style.display = 'none';
        document.getElementById('previewSection').classList.remove('d-none');
        document.getElementById('fileNameBadge').textContent = file.name;
        document.getElementById('fileSizeBadge').textContent = formatFileSize(file.size);
        document.getElementById('analyzeBtn').disabled = false;
    };
    reader.readAsDataURL(file);
}

function clearImage() {
    selectedFile = null;
    document.getElementById('fileInput').value = '';
    document.getElementById('previewImage').src = '';
    document.getElementById('dropzoneContent').style.display = '';
    document.getElementById('previewSection').classList.add('d-none');
    document.getElementById('analyzeBtn').disabled = true;

    // Reset panels
    document.getElementById('initialPanel').classList.remove('d-none');
    document.getElementById('resultPanel').classList.add('d-none');
    document.getElementById('loadingPanel').classList.add('d-none');
}

// ---- Analysis ----
function analyzeImage() {
    if (!selectedFile) return;

    const modelType = document.querySelector('input[name="modelType"]:checked')?.value || 'LOCAL';

    // Show loading
    document.getElementById('initialPanel').classList.add('d-none');
    document.getElementById('resultPanel').classList.add('d-none');
    document.getElementById('loadingPanel').classList.remove('d-none');

    const loadingMessages = [
        '이미지 전처리 중...',
        'AI 모델 추론 실행 중...',
        '결함 탐지 패턴 분석 중...',
        '결과 후처리 및 분류 중...'
    ];
    let msgIndex = 0;
    const msgEl = document.getElementById('loadingMessage');
    const msgInterval = setInterval(() => {
        if (msgEl) {
            msgEl.textContent = loadingMessages[msgIndex % loadingMessages.length];
            msgIndex++;
        }
    }, 600);

    const formData = new FormData();
    formData.append('image', selectedFile);
    formData.append('modelType', modelType);

    fetch('/api/vision/analyze', {
        method: 'POST',
        body: formData
    })
    .then(res => {
        if (!res.ok) throw new Error('서버 오류: ' + res.status);
        return res.json();
    })
    .then(result => {
        clearInterval(msgInterval);
        document.getElementById('loadingPanel').classList.add('d-none');
        document.getElementById('resultPanel').classList.remove('d-none');
        renderResults(result);
    })
    .catch(err => {
        clearInterval(msgInterval);
        document.getElementById('loadingPanel').classList.add('d-none');
        document.getElementById('initialPanel').classList.remove('d-none');
        showError('분석 중 오류가 발생했습니다: ' + err.message);
    });
}

function renderResults(result) {
    // Summary card header
    const header = document.getElementById('resultHeader');
    const headerText = document.getElementById('resultHeaderText');
    const headerBadge = document.getElementById('resultHeaderBadge');

    if (result.defectDetected) {
        header.style.borderBottom = '3px solid #da3633';
        headerText.innerHTML = '<i class="bi bi-exclamation-triangle-fill text-danger me-2"></i>결함 탐지됨';
        headerBadge.className = 'badge bg-danger';
        headerBadge.textContent = '점검 필요';
    } else {
        header.style.borderBottom = '3px solid #238636';
        headerText.innerHTML = '<i class="bi bi-shield-check text-success me-2"></i>이상 없음';
        headerBadge.className = 'badge bg-success';
        headerBadge.textContent = '정상';
    }

    // Verdict
    const verdictCard = document.getElementById('verdictCard');
    const verdictIcon = document.getElementById('verdictIcon');
    const verdictText = document.getElementById('verdictText');
    const verdictSubtext = document.getElementById('verdictSubtext');

    if (result.defectDetected) {
        verdictCard.style.background = 'rgba(218,54,51,0.08)';
        verdictCard.style.border = '1px solid rgba(218,54,51,0.3)';
        verdictIcon.innerHTML = '<i class="bi bi-exclamation-triangle-fill text-danger"></i>';
        verdictText.innerHTML = '<span style="color:#f85149">결함 탐지</span>';
        verdictSubtext.textContent = '블레이드 이상 징후가 감지되었습니다. 즉각 점검을 권고합니다.';
    } else {
        verdictCard.style.background = 'rgba(35,134,54,0.08)';
        verdictCard.style.border = '1px solid rgba(35,134,54,0.3)';
        verdictIcon.innerHTML = '<i class="bi bi-shield-check-fill text-success"></i>';
        verdictText.innerHTML = '<span style="color:#3fb950">정상 상태</span>';
        verdictSubtext.textContent = '탐지된 결함이 없습니다. 정기 점검을 유지하세요.';
    }

    // Stats
    setText('statProcessingTime', result.processingTimeMs + ' ms');
    setText('statModelUsed', result.modelUsed || '--');
    const detections = result.detections || [];
    setText('statDetectionCount', detections.length);
    const maxConf = detections.length > 0
        ? Math.max(...detections.map(d => d.confidence || 0))
        : 0;
    setText('statMaxConfidence', detections.length > 0 ? (maxConf * 100).toFixed(1) + '%' : '--');

    // Detections list
    const listEl = document.getElementById('detectionsList');
    const noDefectMsg = document.getElementById('noDefectMsg');

    if (detections.length === 0) {
        noDefectMsg.classList.remove('d-none');
        listEl.innerHTML = '';
    } else {
        noDefectMsg.classList.add('d-none');
        listEl.innerHTML = detections.map((d, i) => {
            const conf = Math.round((d.confidence || 0) * 100);
            const confColor = conf >= 85 ? '#da3633' : conf >= 70 ? '#d29922' : '#1f6feb';
            const bbox = d.bbox || [0, 0, 0, 0];
            const defectLabel = getDefectLabel(d.label);
            return `
            <div class="ww-detection-item">
                <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                        <span class="badge" style="background:${confColor}20;color:${confColor};border:1px solid ${confColor}40;font-size:0.75rem;">
                            <i class="bi bi-bug me-1"></i>${d.label || 'Unknown'}
                        </span>
                        <span class="text-muted small ms-2">${defectLabel}</span>
                    </div>
                    <span class="fw-bold" style="color:${confColor}; font-size:1.1rem">${conf}%</span>
                </div>
                <div class="ww-confidence-bar">
                    <div class="ww-confidence-fill" style="width:${conf}%; background:${confColor}"></div>
                </div>
                <div class="mt-2 text-muted small">
                    <i class="bi bi-bounding-box me-1"></i>
                    탐지 영역: (${bbox[0]}, ${bbox[1]}) ~ (${bbox[0]+bbox[2]}, ${bbox[1]+bbox[3]})
                    &nbsp;|&nbsp; 크기: ${bbox[2]}×${bbox[3]}px
                </div>
            </div>`;
        }).join('');
    }
}

function getDefectLabel(label) {
    const labels = {
        'blade_crack': '블레이드 균열',
        'surface_erosion': '표면 침식',
        'lightning_damage': '낙뢰 손상',
        'leading_edge_erosion': '전연부 침식',
        'leading_edge_pitting': '전연부 피팅',
        'delamination': '복합재료 박리'
    };
    return labels[label] || label;
}

// ---- Utils ----
function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function showError(msg) {
    const toast = document.createElement('div');
    toast.className = 'position-fixed bottom-0 end-0 p-3';
    toast.style.zIndex = 9999;
    toast.innerHTML = `
        <div class="toast show align-items-center text-bg-danger border-0">
            <div class="d-flex">
                <div class="toast-body"><i class="bi bi-exclamation-triangle me-2"></i>${msg}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.closest('.position-fixed').remove()"></button>
            </div>
        </div>`;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 5000);
}

// ---- 터빈 모달에서 전달된 컨텍스트 배너 표시 ----
document.addEventListener('DOMContentLoaded', function () {
    const raw = sessionStorage.getItem('ww_inspection_context');
    if (!raw) return;
    sessionStorage.removeItem('ww_inspection_context');
    try {
        const d = JSON.parse(raw);
        const statusColor = { CRITICAL: 'danger', WARNING: 'warning', STOPPED: 'secondary' }[d.status] || 'info';
        const statusKor   = { NORMAL: '정상', WARNING: '경고', CRITICAL: '긴급', STOPPED: '정지' }[d.status] || d.status;
        const banner = document.createElement('div');
        banner.className = `alert alert-${statusColor} d-flex align-items-center gap-3 mb-3`;
        banner.innerHTML = `
            <i class="bi bi-wind fs-4"></i>
            <div>
                <strong>${d.turbineId} 터빈 결함 검사 모드</strong>
                <div class="small mt-1">
                    상태: <strong>${statusKor}</strong> &nbsp;|&nbsp;
                    기어박스: <strong>${(d.gearboxTemp || 0).toFixed(1)}°C</strong> &nbsp;|&nbsp;
                    진동: <strong>${(d.vibration || 0).toFixed(2)} mm/s</strong> &nbsp;|&nbsp;
                    발전량: <strong>${Math.round(d.powerOutput || 0)} kW</strong> &nbsp;|&nbsp;
                    풍속: <strong>${(d.windSpeed || 0).toFixed(1)} m/s</strong>
                </div>
            </div>`;
        const header = document.querySelector('.ww-page-header');
        if (header) header.after(banner);
        // 해당 터빈 정보를 dropzone 안내 문구에도 반영
        const hint = document.querySelector('#dropzoneContent p');
        if (hint) hint.textContent = `${d.turbineId} 터빈 블레이드 이미지를 업로드하세요`;
    } catch (e) {}
});
