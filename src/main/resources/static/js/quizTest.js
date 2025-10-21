let currentIndex = 0;
let answers = {};

function renderQuestion(index) {
    const q = questions[index];

    document.getElementById('quizNumber').textContent = `${index+1}/${questions.length}`;
    document.getElementById('quizTitle').textContent = `${index+1}번`;
    document.getElementById('quizText').textContent = q.question;

    const container = document.getElementById('quizAnswerContainer');
    container.innerHTML = '';

    // ⭐ MULTIPLE_CHOICE만 객관식 ⭐
    if (q.questionType === 'MULTIPLE_CHOICE') {
        // 객관식
        if (q.options && q.options.length > 0) {
            q.options.forEach((opt, idx) => {
                const id = `opt-${index}-${idx}`;
                const div = document.createElement('div');
                div.classList.add('checkbox-container');

                const input = document.createElement('input');
                input.type = 'radio';
                input.name = `question-${index}`;
                input.id = id;
                input.value = opt;

                // ⭐ 수정: answers는 객체! ⭐
                if(answers[index] === opt) {
                    input.checked = true;
                }

                const label = document.createElement('label');
                label.htmlFor = id;
                label.textContent = opt;

                div.appendChild(input);
                div.appendChild(label);
                container.appendChild(div);
            });
        } else {
            container.innerHTML = '<p style="color:red;">⚠️ 보기가 없습니다.</p>';
        }
    } else {
        // ⭐ 주관식 ⭐
        const input = document.createElement('input');
        input.type = 'text';
        input.placeholder = '답안을 입력하세요';
        input.name = `question-${index}`;
        input.classList.add('quiz-answer');

        if(answers[index]) {
            input.value = answers[index];
        }

        container.appendChild(input);
    }

    updateButtons();
}

function saveAnswer() {
    const q = questions[currentIndex];
    const container = document.getElementById('quizAnswerContainer');

    // 객관식
    if (q.questionType === 'MULTIPLE_CHOICE' || q.questionType === 'CONCEPT') {
        const checked = container.querySelector('input[type=radio]:checked');
        if(checked) {
            answers[currentIndex] = checked.value;
        }
    }
    // 주관식
    else {
        const textInput = container.querySelector('input[type=text]');
        if(textInput) {
            answers[currentIndex] = textInput.value.trim();
        }
    }
}

function updateButtons() {
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');

    prevBtn.style.opacity = currentIndex === 0 ? '0.3' : '1';
    nextBtn.style.opacity = currentIndex === questions.length - 1 ? '0.3' : '1';
}

function shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

function initQuiz() {
    // 객관식 문제들의 보기 섞기
    questions.forEach(q => {
        if ((q.questionType === 'MULTIPLE_CHOICE' || q.questionType === 'CONCEPT')
            && q.options && q.options.length > 0) {
            q.options = shuffleArray(q.options);
        }
    });

    // 첫 문제 렌더링
    renderQuestion(currentIndex);

    // 이전 버튼
    document.getElementById('prevBtn').addEventListener('click', () => {
        saveAnswer();
        if(currentIndex > 0) {
            currentIndex--;
            renderQuestion(currentIndex);
        }
    });

    // 다음 버튼
    document.getElementById('nextBtn').addEventListener('click', () => {
        saveAnswer();
        if(currentIndex < questions.length - 1) {
            currentIndex++;
            renderQuestion(currentIndex);
        }
    });

    // 제출 버튼
    document.getElementById('submitBtn').addEventListener('click', async () => {
        saveAnswer();

        // 미응답 문제 확인
        const unanswered = questions.filter((q, idx) => !answers[idx]);
        if (unanswered.length > 0) {
            if (!confirm(`${unanswered.length}개 문제가 미응답입니다. 제출하시겠습니까?`)) {
                return;
            }
        }

        try {
            const response = await fetch('/exam/api/submit', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    testIdx: testIdx,
                    answers: answers,
                    startTime: startTime,
                    endTime: new Date().toISOString()
                })
            });

            const data = await response.json();

            if (data.success) {
                alert('제출 완료!');
                window.location.href = `/exam/result/${data.resultIdx}`;
            } else {
                alert('제출 실패: ' + (data.message || '알 수 없는 오류'));
            }
        } catch (err) {
            console.error('제출 에러:', err);
            alert('제출 중 오류가 발생했습니다.');
        }
    });
}

// 페이지 로드 시 초기화
document.addEventListener('DOMContentLoaded', initQuiz);
