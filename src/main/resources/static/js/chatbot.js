// chatbot.js

let currentSessionId = null;

// CSRF 토큰 가져오기 (DOMContentLoaded 밖에서 정의하여 전역적으로 사용 가능)
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    return {
        token: token ? token.getAttribute('content') : null,
        header: header ? header.getAttribute('content') : null
    };
}

// HTML 이스케이프 (XSS 방지) (전역 함수)
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 새 대화 시작 (옵션) (전역 함수)
function startNewChat() {
    currentSessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
    const chatBody = document.getElementById('chatBody');
    if (chatBody) {
        chatBody.innerHTML = '<div class="message bot">새 대화를 시작합니다. 무엇을 도와드릴까요?</div>';
    }
}

// 평가 기능 (별점) (전역 함수)
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

// 통계 기능 (전역 함수)
async function loadChatStats() {
    try {
        const response = await fetch('/api/chat/stats');
        const stats = await response.json();

        console.log('총 대화 수:', stats.totalChats);
        console.log('평균 응답 시간:', stats.avgResponseTimeMs + 'ms');
        console.log('평균 평점:', stats.avgRating);
    } catch (error) {
        console.error('통계 로드 실패:', error);
    }
}

// 히스토리 로드 (전역 함수)
async function loadChatHistory() {
    try {
        const response = await fetch(`/api/chat/history?sessionId=${currentSessionId}`);
        const history = await response.json();

        const chatBody = document.getElementById('chatBody');
        if (!chatBody) return; // 요소 없으면 종료

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
        if(chatBody) chatBody.innerHTML = '<div class="message bot">안녕하세요! 무엇을 도와드릴까요?</div>';
    }
}


// ------------------------------------------------------------------
// ✅ DOMContentLoaded: DOM 요소가 완전히 로드된 후 실행
// ------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', () => {
    // 모달 및 입력 요소 가져오기
    const aiButton = document.getElementById('aiButton');
    const gptModal = document.getElementById('gptModal');
    const closeModal = document.getElementById('closeModal');
    const sendBtn = document.getElementById('sendBtn');
    const userInput = document.getElementById('userInput');
    const chatBody = document.getElementById('chatBody');

    // 1. AI 버튼 클릭 이벤트 (모달 열기 및 세션 시작)
    if (aiButton && gptModal) {
        aiButton.addEventListener('click', () => {
            gptModal.style.display = 'flex'; // 모달 표시
            if (!currentSessionId) {
                currentSessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
            }
            loadChatHistory();
        });
    }

    // 2. 모달 닫기 버튼 클릭 이벤트
    if (closeModal && gptModal) {
        closeModal.addEventListener('click', () => {
            gptModal.style.display = 'none';
        });
    }

    // 3. 모달 외부 클릭 이벤트
    window.addEventListener('click', (e) => {
        if (e.target === gptModal) gptModal.style.display = 'none';
    });

    // 4. 메시지 전송 이벤트
    if (sendBtn && userInput && chatBody) {
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
                const csrf = getCsrfToken();

                if (!csrf.token || !csrf.header) {
                    console.error('CSRF 토큰이 없습니다! 페이지를 새로고침하세요.');
                    throw new Error('CSRF 토큰이 없습니다.');
                }

                const headers = { 'Content-Type': 'application/json' };
                headers[csrf.header] = csrf.token; // CSRF 토큰 헤더에 추가

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

        // 5. Enter 키 처리
        userInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendBtn.click();
            }
        });
    }
});