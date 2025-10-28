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

function getCsrfInput() {
    const csrf = getCsrfToken();
    return `<input type="hidden" name="${csrf.header}" value="${csrf.token}">`;
}

function renderMarkdown(text) {
    if (typeof marked !== 'undefined') {
        return marked.marked ? marked.marked(text) : marked.parse(text);
    }
    return escapeHtml(text);
}

// HTML 이스케이프 (XSS 방지) (전역 함수)
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function renderBotMessage(text) {
    // [FORM:POST:/url:param=value:버튼텍스트] 파싱
    const formPattern = /\[FORM:(POST|GET):([^:]+):([^:]*):([^\]]+)\]/g;
    let html = renderMarkdown(text.replace(formPattern, '')); // Form 태그 제거하고 마크다운 렌더링

    // Form 버튼 추가
    let match;
    while ((match = formPattern.exec(text)) !== null) {
        const [, method, url, params, buttonText] = match;

        let formHtml = `<form method="${method}" action="${url}" style="display: inline-block; margin: 5px;">`;

        if (method === 'POST') {
            formHtml += getCsrfInput();
        }

        if (params) {
            const paramPairs = params.split('&');
            paramPairs.forEach(pair => {
                if (pair.trim()) {
                    const [key, value] = pair.split('=');
                    if (key && value !== undefined) {
                        formHtml += `<input type="hidden" name="${key}" value="${decodeURIComponent(value)}">`;
                    }
                }
            });
        }

        formHtml += `<button type="submit" class="btn btn-primary btn-sm">${buttonText}</button>`;
        formHtml += `</form>`;

        html += formHtml;
    }

    return html;
}

// 채팅 기록 로드 (실제 ID 사용)
function loadChatHistory() {
    const chatBody = document.getElementById('chatBody'); // ✅ 실제 ID
    if (!chatBody) {
        console.warn('❌ chatBody element not found');
        return;
    }

    const chatData = localStorage.getItem('chatHistory');
    if (chatData) {
        try {
            const parsedData = JSON.parse(chatData);
            if (parsedData.sessionId) {
                currentSessionId = parsedData.sessionId;
                if (parsedData.messages && parsedData.messages.length > 0) {
                    chatBody.innerHTML = '<div class="message bot">안녕하세요! 무엇을 도와드릴까요?</div>'; // 기본 메시지 유지
                    parsedData.messages.forEach(msg => {
                        addChatMessage(msg.sender, msg.message, false);
                    });
                }
            }
        } catch (e) {
            console.error('Error loading chat history:', e);
        }
    }

    if (!currentSessionId) {
        currentSessionId = 'session_' + Date.now();
    }

    console.log('✅ Chat history loaded, sessionId:', currentSessionId);
}

// 채팅 기록 저장
function saveChatHistory() {
    const chatBody = document.getElementById('chatBody'); // ✅ 실제 ID
    if (!chatBody) return;

    const messages = [];
    const messageElements = chatBody.querySelectorAll('.user-message, .bot-message');

    messageElements.forEach(element => {
        // 퀵 버튼 컨테이너는 제외
        if (element.classList.contains('quick-buttons-container')) return;

        const isUser = element.classList.contains('user-message');
        const messageText = element.textContent || element.innerText || '';

        if (messageText.trim()) {
            messages.push({
                sender: isUser ? 'user' : 'bot',
                message: messageText.trim()
            });
        }
    });

    const chatData = {
        sessionId: currentSessionId,
        messages: messages,
        timestamp: Date.now()
    };

    localStorage.setItem('chatHistory', JSON.stringify(chatData));
}

// 채팅 메시지 추가 (실제 ID 사용)
function addChatMessage(sender, message, shouldSave = true) {
    const chatBody = document.getElementById('chatBody'); // ✅ 실제 ID
    if (!chatBody) return;

    const messageDiv = document.createElement('div');
    messageDiv.className = sender === 'user' ? 'user-message' : 'bot-message';

    const messageContent = document.createElement('div');
    messageContent.className = 'message';
    messageContent.classList.add(sender);
    messageContent.style.cssText = `
        margin: 10px 0;
        padding: 10px 15px;
        border-radius: 15px;
        word-wrap: break-word;
        max-width: 80%;
        ${sender === 'user' 
            ? 'background-color: #007bff; color: white; margin-left: auto; text-align: right;' 
            : 'background-color: #f8f9fa; color: #333; margin-right: auto; border: 1px solid #dee2e6;'}
    `;

    if (sender === 'bot') {
        messageContent.innerHTML = renderBotMessage(message);
    } else {
        messageContent.textContent = message;
    }

    messageDiv.appendChild(messageContent);
    chatBody.appendChild(messageDiv);
    chatBody.scrollTop = chatBody.scrollHeight;

    if (shouldSave) {
        saveChatHistory();
    }
}

// 메시지 전송 (실제 ID 사용)
async function sendMessage() {
    const input = document.getElementById('userInput'); // ✅ 실제 ID (chatInput이 아니라 userInput!)
    if (!input) {
        console.error('❌ userInput element not found');
        return;
    }

    const message = input.value.trim();
    if (!message) return;

    // 사용자 메시지 추가
    addChatMessage('user', message);
    input.value = '';

    // 로딩 메시지 추가
    addChatMessage('bot', '답변을 생성하고 있습니다...', false);

    try {
        const csrf = getCsrfToken();
        const response = await fetch('/api/chat/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrf.header]: csrf.token
            },
            body: JSON.stringify({
                message: message,
                sessionId: currentSessionId
            })
        });

        // 로딩 메시지 제거
        const chatBody = document.getElementById('chatBody');
        if (chatBody && chatBody.lastChild) {
            chatBody.removeChild(chatBody.lastChild);
        }

        if (response.ok) {
            const data = await response.json();
            addChatMessage('bot', data.reply || '응답을 받지 못했습니다.');
        } else {
            addChatMessage('bot', '죄송합니다. 오류가 발생했습니다.');
        }
    } catch (error) {
        console.error('Chat error:', error);
        // 로딩 메시지 제거
        const chatBody = document.getElementById('chatBody');
        if (chatBody && chatBody.lastChild) {
            chatBody.removeChild(chatBody.lastChild);
        }
        addChatMessage('bot', '네트워크 오류가 발생했습니다.');
    }
}

// 새 대화 시작 (전역 함수로 만들어서 HTML onclick에서 호출 가능)
function startNewChat() {
    currentSessionId = 'session_' + Date.now();
    const chatBody = document.getElementById('chatBody');
    if (chatBody) {
        chatBody.innerHTML = '<div class="message bot">안녕하세요! 무엇을 도와드릴까요?</div>';
        addQuickButtons();
    }
    localStorage.removeItem('chatHistory');
    console.log('✅ New chat started');
}

// ============================================================================
// 퀵 버튼 기능 추가
// ============================================================================

// 퀵 버튼 추가 함수
function addQuickButtons() {
    const chatBody = document.getElementById('chatBody'); // ✅ 실제 ID
    if (!chatBody) return;

    // 이미 퀵 버튼이 있으면 제거
    const existing = chatBody.querySelector('.quick-buttons-container');
    if (existing) {
        existing.remove();
    }

    const quickButtonsHtml = `
        <div class="quick-buttons-container" style="
            margin: 15px 0; 
            padding: 15px; 
            background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); 
            border-radius: 10px; 
            border: 1px solid #dee2e6;
            text-align: center;
        ">
            <div style="font-size: 14px; font-weight: 600; color: #495057; margin-bottom: 10px;">
                🚀 빠른 바로가기
            </div>
            <div style="display: flex; flex-wrap: wrap; gap: 8px; justify-content: center;">
                <button class="quick-btn" data-action="lectures" style="
                    background: linear-gradient(135deg, #007bff, #0056b3); 
                    color: white; border: none; padding: 8px 14px; 
                    border-radius: 18px; font-size: 12px; cursor: pointer; 
                    font-weight: 500; transition: all 0.3s;
                ">📚 강의 찾기</button>

                <button class="quick-btn" data-action="exams" style="
                    background: linear-gradient(135deg, #dc3545, #c82333); 
                    color: white; border: none; padding: 8px 14px; 
                    border-radius: 18px; font-size: 12px; cursor: pointer; 
                    font-weight: 500; transition: all 0.3s;
                ">📋 시험 만들기</button>

                <button class="quick-btn" data-action="notes" style="
                    background: linear-gradient(135deg, #28a745, #1e7e34); 
                    color: white; border: none; padding: 8px 14px; 
                    border-radius: 18px; font-size: 12px; cursor: pointer; 
                    font-weight: 500; transition: all 0.3s;
                ">📝 노트 관리</button>

                <button class="quick-btn" data-action="schedule" style="
                    background: linear-gradient(135deg, #ffc107, #e0a800); 
                    color: #212529; border: none; padding: 8px 14px; 
                    border-radius: 18px; font-size: 12px; cursor: pointer; 
                    font-weight: 500; transition: all 0.3s;
                ">📅 일정 관리</button>
            </div>
            <div style="font-size: 11px; color: #6c757d; margin-top: 8px;">
                💡 대화 내용의 키워드가 자동으로 검색에 반영됩니다
            </div>
        </div>
    `;

    // chatBody의 시작 부분에 추가 (기본 메시지 다음)
    const firstMessage = chatBody.querySelector('.message.bot');
    if (firstMessage && firstMessage.nextSibling) {
        firstMessage.insertAdjacentHTML('afterend', quickButtonsHtml);
    } else {
        chatBody.insertAdjacentHTML('beforeend', quickButtonsHtml);
    }

    // 버튼 이벤트 리스너 추가
    const quickBtns = chatBody.querySelectorAll('.quick-btn');
    quickBtns.forEach(btn => {
        btn.addEventListener('click', handleQuickButtonClick);

        // 호버 효과
        btn.addEventListener('mouseenter', () => {
            btn.style.transform = 'translateY(-2px)';
            btn.style.boxShadow = '0 4px 8px rgba(0,0,0,0.2)';
        });
        btn.addEventListener('mouseleave', () => {
            btn.style.transform = 'translateY(0)';
            btn.style.boxShadow = 'none';
        });
    });

    console.log('✅ Quick buttons added');
}

// 퀵 버튼 클릭 처리
async function handleQuickButtonClick(e) {
    const action = e.target.dataset.action;

    // 클릭 애니메이션
    e.target.style.transform = 'scale(0.95)';
    setTimeout(() => {
        e.target.style.transform = 'scale(1)';
    }, 100);

    // 최근 키워드 추출 (기술 키워드 우선)
    const keywords = extractTechKeywords();
    console.log('🎯 추출된 키워드:', keywords);

    switch(action) {
        case 'lectures':
            await goToLectures(keywords);  // NotionComplete 방식
            break;

        case 'exams':
            await goToExams(keywords);     // 세션 방식
            break;

        case 'notes':
            goToNotes(keywords);           // GET 파라미터 방식
            break;

        case 'schedule':
            goToSchedule();                // 단순 이동
            break;
    }
}

// 간단한 키워드 추출
function extractTechKeywords() {
    const chatBody = document.getElementById('chatBody');
    if (!chatBody) return [];

    const userMessages = chatBody.querySelectorAll('.user-message');
    if (userMessages.length === 0) return [];

    // 최근 3개 메시지에서만 추출
    const recentMessages = Array.from(userMessages).slice(-3);
    const keywordSet = new Set();

    // 확장된 기술 키워드 리스트
    const techKeywords = [
        // 프로그래밍 언어
        '파이썬', 'python', 'py',
        '자바', 'java', 
        'javascript', 'js', '자바스크립트',
        'typescript', 'ts', '타입스크립트',
        'c++', 'cpp', 'c언어',
        'php', 'ruby', 'go', 'rust', 'kotlin', 'swift',

        // 프레임워크/라이브러리
        '스프링', 'spring', '스프링부트', 'springboot',
        '리액트', 'react', '뷰', 'vue', '앵귤러', 'angular',
        'node', 'nodejs', 'express', 'django', 'flask',

        // 데이터베이스
        'mysql', 'postgresql', '오라클', 'oracle',
        'mongodb', '몽고db', 'redis', 'sqlite',
        'sql', '데이터베이스', 'database', 'db',

        // 웹 기술
        'html', 'css', 'sass', 'scss',
        'bootstrap', 'tailwind', 'jquery',
        'rest', 'api', 'graphql', 'ajax',

        // 데이터 과학/AI
        '머신러닝', 'machinelearning', 'ml',
        '딥러닝', 'deeplearning', 'dl',
        'ai', '인공지능', '데이터분석', '데이터사이언스',
        'tensorflow', 'pytorch', 'pandas', 'numpy',

        // 기타 기술
        '알고리즘', '자료구조', '네트워크', '보안',
        '클라우드', 'aws', 'azure', 'gcp',
        'docker', '도커', 'kubernetes', 'k8s',
        'git', '깃', '깃허브', 'github'
    ];

    recentMessages.forEach(msg => {
        const messageText = (msg.textContent || '').toLowerCase().trim();

        // 기술 키워드 매칭 (우선순위)
        techKeywords.forEach(keyword => {
            const lowerKeyword = keyword.toLowerCase();
            if (messageText.includes(lowerKeyword)) {
                // 원본 키워드 형태로 저장 (대소문자 유지)
                keywordSet.add(keyword);
            }
        });
    });

    // 최대 5개 키워드만 반환
    const results = Array.from(keywordSet).slice(0, 5);

    // 한글 키워드 우선 정렬
    return results.sort((a, b) => {
        const aIsKorean = /[가-힣]/.test(a);
        const bIsKorean = /[가-힣]/.test(b);
        if (aIsKorean && !bIsKorean) return -1;
        if (!aIsKorean && bIsKorean) return 1;
        return 0;
    });
}

// 페이지 이동 함수들
async function goToLectures(keywords) {
    if (keywords && keywords.length > 0) {
        // 태그 배열로 payload 생성
        const payload = { 
            tags: keywords, 
            like: false, 
            size: 30 
        };
        
        // sessionStorage에 저장 (NotionComplete와 동일!)
        sessionStorage.setItem('lectureRecommendPayload', JSON.stringify(payload));
        
        // '/lecture' URL로 이동 (NotionComplete와 동일!)
        window.open('/lecture', '_blank');
        
        addChatMessage('bot', `📚 "${keywords.join(', ')}" 관련 강의를 검색합니다!`);
    }
}

async function goToExams(keywords) {
    try {
        if (keywords && keywords.length > 0) {
            addChatMessage('bot', `📋 "${keywords.join(', ')}" 관련 시험을 준비하고 있습니다...`, false);

            const csrf = getCsrfToken();

            // 시험 생성에 사용할 키워드를 세션에 저장
            const response = await fetch('/exam/api/set-keywords', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest',
                    [csrf.header]: csrf.token
                },
                body: JSON.stringify({
                    keywords: keywords
                })
            });

            // 성공/실패와 관계없이 시험 페이지로 이동
            setTimeout(() => {
                window.open('/exam/create', '_blank');
            }, 100);

            addChatMessage('bot', `📋 "${keywords.join(', ')}" 키워드가 적용된 시험 생성 페이지로 이동합니다!`, false);

        } else {
            window.open('/exam/create', '_blank');
            addChatMessage('bot', '📋 시험 생성 페이지로 이동합니다!', false);
        }
    } catch (error) {
        console.error('시험 준비 오류:', error);
        window.open('/exam/create', '_blank');
        addChatMessage('bot', '📋 시험 생성 페이지로 이동합니다!', false);
    }
}

function goToNotes(keywords) {
    try {
        if (keywords && keywords.length > 0) {
            const searchParam = keywords.join(' ');
            // 노트 관리는 GET 파라미터 방식 사용 (검색 기능 지원)
            window.open(`/notion/manage?search=${encodeURIComponent(searchParam)}`, '_blank');
            addChatMessage('bot', `📝 "${searchParam}" 관련 노트를 검색합니다!`, false);
        } else {
            window.open('/notion/manage', '_blank');
            addChatMessage('bot', '📝 노트 관리 페이지로 이동합니다!', false);
        }
    } catch (error) {
        console.error('노트 페이지 이동 오류:', error);
        window.open('/notion/manage', '_blank');
        addChatMessage('bot', '📝 노트 관리 페이지로 이동합니다!', false);
    }
}

function goToSchedule() {
    window.open('/schedule', '_blank');
    addChatMessage('bot', '📅 일정 관리 페이지로 이동합니다!', false);
}

// ============================================================================
// 모달 이벤트 처리 (실제 ID 사용)
// ============================================================================

document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 DOM loaded, initializing chatbot...');

    // 기존 초기화
    loadChatHistory();

    // Enter 키 이벤트 (실제 ID 사용)
    const userInput = document.getElementById('userInput'); // ✅ 실제 ID
    if (userInput) {
        userInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
        console.log('✅ User input event listener added');
    } else {
        console.warn('❌ userInput element not found');
    }

    // 전송 버튼 이벤트 (실제 ID 사용)
    const sendBtn = document.getElementById('sendBtn'); // ✅ 실제 ID
    if (sendBtn) {
        sendBtn.addEventListener('click', sendMessage);
        console.log('✅ Send button event listener added');
    } else {
        console.warn('❌ sendBtn element not found');
    }

    // AI 버튼 클릭으로 모달 열기 (실제 ID 사용)
    const aiButton = document.getElementById('aiButton'); // ✅ 실제 ID
    const gptModal = document.getElementById('gptModal'); // ✅ 실제 ID
    const closeModal = document.getElementById('closeModal'); // ✅ 실제 ID

    if (aiButton && gptModal) {
        aiButton.addEventListener('click', function() {
            gptModal.style.display = 'flex'; // 모달 열기
            setTimeout(() => {
                addQuickButtons(); // 퀵 버튼 추가
            }, 100);
            console.log('✅ Chat modal opened');
        });
        console.log('✅ AI button event listener added');
    }

    if (closeModal && gptModal) {
        closeModal.addEventListener('click', function() {
            gptModal.style.display = 'none'; // 모달 닫기
            console.log('✅ Chat modal closed');
        });
    }

    // 모달 배경 클릭으로 닫기
    if (gptModal) {
        gptModal.addEventListener('click', function(e) {
            if (e.target === gptModal) {
                gptModal.style.display = 'none';
                console.log('✅ Chat modal closed by background click');
            }
        });
    }

    console.log('🎉 Chatbot initialization complete with correct IDs!');
});