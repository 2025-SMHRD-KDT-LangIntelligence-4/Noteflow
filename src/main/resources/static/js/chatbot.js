// chatbot.js
// chatbot.js

let currentSessionId = null;

// CSRF 토큰 가져오기
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    return {
        token: token ? token.getAttribute('content') : null,
        header: header ? header.getAttribute('content') : null
    };
}

// 모달창 제어
const aiButton = document.getElementById('aiButton');
const gptModal = document.getElementById('gptModal');
const closeModal = document.getElementById('closeModal');

aiButton.addEventListener('click', () => {
    gptModal.style.display = 'flex';
    if (!currentSessionId) {
        currentSessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    }
    loadChatHistory();
});

closeModal.addEventListener('click', () => {
    gptModal.style.display = 'none';
});

window.addEventListener('click', (e) => {
    if (e.target === gptModal) gptModal.style.display = 'none';
});

// 히스토리 로드
async function loadChatHistory() {
    try {
        const response = await fetch(`/api/chat/history?sessionId=${currentSessionId}`);
        const history = await response.json();

        const chatBody = document.getElementById('chatBody');
        chatBody.innerHTML = '';

        if (history.length === 0) {
            chatBody.innerHTML = '<div class="message bot">안녕하세요! 무엇을 도와드릴까요?</div>';
        } else {
            history.forEach(chat => {
                chatBody.innerHTML += `<div class="message user">${escapeHtml(chat.question)}</div>`;
                chatBody.innerHTML += `<div class="message bot">${escapeHtml(chat.answer)}</div>`;
            });
        }

        chatBody.scrollTop = chatBody.scrollHeight;
    } catch (error) {
        console.error('히스토리 로드 실패:', error);
        const chatBody = document.getElementById('chatBody');
        chatBody.innerHTML = '<div class="message bot">안녕하세요! 무엇을 도와드릴까요?</div>';
    }
}

// 메시지 전송 (CSRF 토큰 포함)
const sendBtn = document.getElementById('sendBtn');
const userInput = document.getElementById('userInput');
const chatBody = document.getElementById('chatBody');

sendBtn.addEventListener('click', async () => {
    const text = userInput.value.trim();
    if (!text) return;

    chatBody.innerHTML += `<div class="message user">${escapeHtml(text)}</div>`;
    userInput.value = '';

    const loadingId = 'loading-' + Date.now();
    chatBody.innerHTML += `
        <div class="message bot loading" id="${loadingId}">
            <span></span><span></span><span></span>
        </div>
    `;
    chatBody.scrollTop = chatBody.scrollHeight;

    try {
        // CSRF 토큰 가져오기
        const csrf = getCsrfToken();

        // ✅ 디버깅: CSRF 토큰 확인
        console.log('CSRF Token:', csrf.token);
        console.log('CSRF Header:', csrf.header);

        if (!csrf.token || !csrf.header) {
            console.error('CSRF 토큰이 없습니다! 페이지를 새로고침하세요.');
            throw new Error('CSRF 토큰이 없습니다.');
        }

        const headers = {
            'Content-Type': 'application/json'
        };

        // CSRF 토큰 헤더에 추가
        headers[csrf.header] = csrf.token;

        console.log('Request headers:', headers);

        const response = await fetch('/api/chat/send', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                message: text,
                sessionId: currentSessionId
            })
        });

        if (!response.ok) {
            throw new Error('서버 오류: ' + response.status);
        }

        const data = await response.json();

        if (data.sessionId) {
            currentSessionId = data.sessionId;
        }

        document.getElementById(loadingId)?.remove();
        chatBody.innerHTML += `<div class="message bot">${escapeHtml(data.reply)}</div>`;
        chatBody.scrollTop = chatBody.scrollHeight;

    } catch (error) {
        document.getElementById(loadingId)?.remove();
        chatBody.innerHTML += `<div class="message bot">죄송합니다. 오류가 발생했습니다: ${error.message}</div>`;
        console.error('메시지 전송 실패:', error);
    }
});

// Enter 키 처리
userInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendBtn.click();
    }
});

// HTML 이스케이프 (XSS 방지)
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}


// 새 대화 시작 (옵션)
function startNewChat() {
    currentSessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    const chatBody = document.getElementById('chatBody');
    chatBody.innerHTML = '<div class="message bot">새 대화를 시작합니다. 무엇을 도와드릴까요?</div>';
}

// 평가 기능 (별점)
async function rateChat(chatIdx, rating) {
    try {
        await fetch(`/api/chat/rate/${chatIdx}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rating: rating, feedback: null })
        });
        console.log('평가 완료');
    } catch (error) {
        console.error('평가 실패:', error);
    }
}
// 통계 기능
async function loadChatStats() {
    try {
        const response = await fetch('/api/chat/stats');
        const stats = await response.json();

        console.log('총 대화 수:', stats.totalChats);
        console.log('평균 응답 시간:', stats.avgResponseTimeMs + 'ms');
        console.log('평균 평점:', stats.avgRating);

        // UI에 표시하려면 여기에 추가
    } catch (error) {
        console.error('통계 로드 실패:', error);
    }
}