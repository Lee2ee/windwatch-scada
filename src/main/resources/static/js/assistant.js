/**
 * WindWatch AI-SCADA - AI Assistant JavaScript
 * LLM-powered failure analysis chat interface
 */

let messageCount = 0;
let isLoading = false;

// ---- Engine Selection ----
document.getElementById('engineSelect')?.addEventListener('change', function () {
    const isCloud = this.value === 'CLOUD';
    document.getElementById('engineInfoLocal').classList.toggle('d-none', isCloud);
    document.getElementById('engineInfoCloud').classList.toggle('d-none', !isCloud);
    document.getElementById('currentEngineLabel').textContent =
        isCloud ? 'GPT-4o Cloud' : 'Llama-3 Local';
    document.getElementById('engineModeNote').textContent =
        isCloud ? '클라우드 처리 - 인터넷 연결이 필요합니다'
                : '로컬 처리 - 데이터가 외부로 전송되지 않습니다';
});

// ---- Input Handling ----
document.getElementById('questionInput')?.addEventListener('input', function () {
    const count = document.getElementById('charCount');
    if (count) count.textContent = this.value.length + '자';
});

function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendQuestion();
    }
}

// ---- Suggested Questions ----
function useSuggestion(btn) {
    const input = document.getElementById('questionInput');
    if (input) {
        input.value = btn.textContent.trim();
        input.focus();
        const count = document.getElementById('charCount');
        if (count) count.textContent = input.value.length + '자';
    }
}

// ---- Send Message ----
function sendQuestion() {
    if (isLoading) return;

    const input = document.getElementById('questionInput');
    const question = input?.value.trim();
    if (!question) return;

    const engine = document.getElementById('engineSelect')?.value || 'LOCAL';

    // Add user message
    appendMessage('user', question);
    input.value = '';
    const count = document.getElementById('charCount');
    if (count) count.textContent = '0자';

    // Show typing indicator
    const typingId = appendTyping();

    isLoading = true;
    document.getElementById('sendBtn').disabled = true;

    fetch('/api/llm/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question, engine })
    })
    .then(res => {
        if (!res.ok) throw new Error('서버 오류: ' + res.status);
        return res.json();
    })
    .then(data => {
        removeTyping(typingId);
        appendAiResponse(data);
    })
    .catch(err => {
        removeTyping(typingId);
        appendMessage('ai', '오류가 발생했습니다: ' + err.message + '\n\n잠시 후 다시 시도해주세요.');
    })
    .finally(() => {
        isLoading = false;
        document.getElementById('sendBtn').disabled = false;
        input?.focus();
    });
}

// ---- Message Rendering ----
function appendMessage(role, text) {
    messageCount++;
    const container = document.getElementById('chatMessages');
    const now = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

    const div = document.createElement('div');
    div.id = 'msg-' + messageCount;

    if (role === 'user') {
        div.className = 'ww-message ww-message-user';
        div.innerHTML = `
            <div class="ww-message-avatar">
                <i class="bi bi-person-fill"></i>
            </div>
            <div class="ww-message-content">
                <div class="ww-message-bubble ww-bubble-user">${escapeHtml(text)}</div>
                <div class="ww-message-time">${now}</div>
            </div>`;
    } else {
        div.className = 'ww-message ww-message-ai';
        div.innerHTML = `
            <div class="ww-message-avatar">
                <i class="bi bi-robot"></i>
            </div>
            <div class="ww-message-content">
                <div class="ww-message-bubble ww-bubble-ai">${formatMarkdown(text)}</div>
                <div class="ww-message-time">WindWatch AI • ${now}</div>
            </div>`;
    }

    container.appendChild(div);
    scrollToBottom();
    return messageCount;
}

function appendAiResponse(data) {
    messageCount++;
    const container = document.getElementById('chatMessages');
    const now = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    const engine = document.getElementById('engineSelect')?.value || 'LOCAL';

    const div = document.createElement('div');
    div.id = 'msg-' + messageCount;
    div.className = 'ww-message ww-message-ai';

    const answer = formatMarkdown(data.answer || '응답 없음');
    const modelInfo = data.modelUsed ? `<small class="text-muted me-2"><i class="bi bi-cpu me-1"></i>${data.modelUsed}</small>` : '';
    const timeInfo = data.processingTimeMs ? `<small class="text-muted"><i class="bi bi-clock me-1"></i>${data.processingTimeMs}ms</small>` : '';

    let refsHtml = '';
    if (data.references && data.references.length > 0) {
        refsHtml = `
            <div class="mt-3">
                <small class="text-muted fw-semibold"><i class="bi bi-book me-1"></i>참고 문서</small>
                ${data.references.map(ref => `
                    <div class="ww-reference-card">
                        <strong><i class="bi bi-file-text me-1"></i>${escapeHtml(ref.title || '')}</strong>
                        <p class="small mt-1 mb-1">${escapeHtml(ref.excerpt || '')}</p>
                        <small class="text-muted"><i class="bi bi-tag me-1"></i>${escapeHtml(ref.source || '')}</small>
                    </div>`).join('')}
            </div>`;
    }

    div.innerHTML = `
        <div class="ww-message-avatar">
            <i class="bi bi-robot"></i>
        </div>
        <div class="ww-message-content" style="max-width:100%">
            <div class="ww-message-bubble ww-bubble-ai">
                ${answer}
                ${refsHtml}
            </div>
            <div class="ww-message-time d-flex align-items-center gap-2">
                ${modelInfo}${timeInfo}
                <span class="text-muted">• ${now}</span>
            </div>
        </div>`;

    container.appendChild(div);
    scrollToBottom();
}

function appendTyping() {
    messageCount++;
    const container = document.getElementById('chatMessages');
    const id = 'typing-' + messageCount;

    const div = document.createElement('div');
    div.id = id;
    div.className = 'ww-message ww-message-ai';
    div.innerHTML = `
        <div class="ww-message-avatar">
            <i class="bi bi-robot"></i>
        </div>
        <div class="ww-message-content">
            <div class="ww-message-bubble ww-bubble-ai">
                <div class="ww-typing">
                    <div class="ww-typing-dot"></div>
                    <div class="ww-typing-dot"></div>
                    <div class="ww-typing-dot"></div>
                    <span class="text-muted small ms-2">AI가 분석 중입니다...</span>
                </div>
            </div>
        </div>`;

    container.appendChild(div);
    scrollToBottom();
    return id;
}

function removeTyping(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

function clearChat() {
    if (!confirm('대화 내용을 모두 삭제하겠습니까?')) return;
    const container = document.getElementById('chatMessages');
    // Keep only the first welcome message
    const messages = container.querySelectorAll('.ww-message');
    messages.forEach((msg, i) => { if (i > 0) msg.remove(); });
    messageCount = 0;
}

// ---- Utilities ----
function scrollToBottom() {
    const container = document.getElementById('chatMessages');
    if (container) {
        setTimeout(() => {
            container.scrollTop = container.scrollHeight;
        }, 50);
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(text || ''));
    return div.innerHTML;
}

function formatMarkdown(text) {
    if (!text) return '';
    let html = escapeHtml(text);

    // Bold **text**
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    // Italic *text*
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    // Code `text`
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Numbered list
    html = html.replace(/^\d+\.\s+(.+)$/gm, '<li>$1</li>');
    // Bullet list
    html = html.replace(/^[-•]\s+(.+)$/gm, '<li>$1</li>');
    // Wrap consecutive li items
    html = html.replace(/(<li>.*<\/li>(\s*<li>.*<\/li>)*)/gs, '<ul class="ps-3 mt-1 mb-1 small">$1</ul>');
    // Line breaks
    html = html.replace(/\n\n/g, '</p><p class="mb-1">');
    html = html.replace(/\n/g, '<br>');
    html = '<p class="mb-1">' + html + '</p>';

    return html;
}

// ---- 터빈 모달에서 전달된 질문 자동 전송 ----
document.addEventListener('DOMContentLoaded', function () {
    const raw = sessionStorage.getItem('ww_assistant_prefill');
    if (!raw) return;
    sessionStorage.removeItem('ww_assistant_prefill');
    try {
        const { turbineId, question } = JSON.parse(raw);
        const input = document.getElementById('questionInput');
        if (!input || !question) return;
        // 터빈 ID 배너 표시
        const banner = document.createElement('div');
        banner.className = 'alert alert-primary d-flex align-items-center gap-2 mx-3 mt-2 py-2';
        banner.style.fontSize = '0.82rem';
        banner.innerHTML = `<i class="bi bi-wind"></i><span><strong>${turbineId}</strong> 터빈 센서 데이터 기반 자동 분석을 시작합니다.</span>`;
        document.getElementById('chatMessages').before(banner);
        // 질문 입력 후 자동 전송
        input.value = question;
        const charCount = document.getElementById('charCount');
        if (charCount) charCount.textContent = question.length + '자';
        setTimeout(() => sendQuestion(), 600);
    } catch (e) {}
});
