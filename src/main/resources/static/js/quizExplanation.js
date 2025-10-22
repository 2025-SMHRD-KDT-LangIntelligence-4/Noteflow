let currentIndex = 0;

function renderExplanation(index) {
    const data = explanationData[index];
    
    // 문제 번호
    document.getElementById('questionNumber').textContent = 
        `${index + 1}/${explanationData.length}`;
    
    // 문제 텍스트
    document.getElementById('questionText').textContent = data.question;
    
    // 답안 컨테이너
    const answerContainer = document.getElementById('answerContainer');
    answerContainer.innerHTML = '';
    
    if (data.questionType === 'MULTIPLE_CHOICE') {
        // 객관식: 보기 표시
        if (data.options && data.options.length > 0) {
            data.options.forEach(opt => {
                const div = document.createElement('div');
                div.classList.add('option-item');
                
                // 정답 표시
                if (opt === data.correctAnswer) {
                    div.classList.add('correct-answer');
                    div.textContent = `✓ ${opt}`;
                } else {
                    div.textContent = opt;
                }
                
                answerContainer.appendChild(div);
            });
        } else {
            answerContainer.innerHTML = '<p>⚠️ 보기가 없습니다.</p>';
        }
    } else {
        // 주관식: 정답만 표시
        const answerDiv = document.createElement('div');
        answerDiv.classList.add('subjective-answer');
        answerDiv.textContent = `정답: ${data.correctAnswer}`;
        answerContainer.appendChild(answerDiv);
    }
    
    // 사용자 답안 표시
    const userAnswerDisplay = document.getElementById('userAnswerDisplay');
    const isCorrect = data.isCorrect;
    userAnswerDisplay.className = 'user-answer-display';
    userAnswerDisplay.classList.add(isCorrect ? 'correct' : 'wrong');
    
    const markIcon = isCorrect ? '✓' : '✗';
    const statusText = isCorrect ? '정답' : '오답';
    userAnswerDisplay.textContent = 
        `${markIcon} ${statusText}: ${data.userAnswer || '(미응답)'}`;
    
    // 해설
    document.getElementById('explanationText').textContent = 
        data.explanation || '해설이 없습니다.';
    
    // 버튼 업데이트
    updateNavigationButtons();
}

function updateNavigationButtons() {
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    
    prevBtn.disabled = (currentIndex === 0);
    nextBtn.disabled = (currentIndex === explanationData.length - 1);
}

function init() {
    renderExplanation(currentIndex);
    
    // 이전 버튼
    document.getElementById('prevBtn').addEventListener('click', () => {
        if (currentIndex > 0) {
            currentIndex--;
            renderExplanation(currentIndex);
        }
    });
    
    // 다음 버튼
    document.getElementById('nextBtn').addEventListener('click', () => {
        if (currentIndex < explanationData.length - 1) {
            currentIndex++;
            renderExplanation(currentIndex);
        }
    });
}

document.addEventListener('DOMContentLoaded', init);
