let currentIndex = 0;
let answers = {}; // 사용자 답안 저장

function renderQuestion(index){
    const q = questions[index];
    document.getElementById('quizNumber').textContent = (index+1) + '/' + questions.length;
    document.getElementById('quizTitle').textContent = '문제 ' + (index+1);
    document.getElementById('quizText').textContent = q.question;

    const container = document.getElementById('quizAnswerContainer');
    container.innerHTML = '';

    if(q.options && q.options.length > 0){
        q.options.forEach((opt, idx) => {
            const id = 'opt-' + index + '-' + idx;
            const div = document.createElement('div');
            div.classList.add('checkbox-container');

            const input = document.createElement('input');
            input.type = 'radio';
            input.name = 'question-' + index;
            input.id = id;
            input.value = opt;
            if(answers[index] === opt) input.checked = true;

            const label = document.createElement('label');
            label.htmlFor = id;
            label.textContent = opt;

            div.appendChild(input);
            div.appendChild(label);
            container.appendChild(div);
        });
    } else {
        const input = document.createElement('input');
        input.type = 'text';
        input.placeholder = '답안을 작성하세요';
        input.name = 'question-' + index;
        input.classList.add('quiz-answer');
        if(answers[index]) input.value = answers[index];
        container.appendChild(input);
    }
}

function saveAnswer(){
    const q = questions[currentIndex];
    const container = document.getElementById('quizAnswerContainer');
    if(q.options && q.options.length > 0){
        const checked = container.querySelector('input[type="radio"]:checked');
        if(checked) answers[currentIndex] = checked.value;
    } else {
        const textInput = container.querySelector('input[type="text"]');
        if(textInput) answers[currentIndex] = textInput.value;
    }
}

function shuffleArray(array){
    if(!Array.isArray(array)) return [];
    return array
        .map(a => [Math.random(), a])
        .sort((a,b) => a[0]-b[0])
        .map(a => a[1]);
}

function initQuiz(){
    // 🔹 안전하게 questions 배열 체크 후 랜덤 20문제
    if(Array.isArray(questions) && questions.length > 20){
        questions = shuffleArray(questions).slice(0, 20);
    }

    document.getElementById('prevBtn').addEventListener('click', ()=>{
        saveAnswer();
        if(currentIndex > 0){
            currentIndex--;
            renderQuestion(currentIndex);
        }
    });

    document.getElementById('nextBtn').addEventListener('click', ()=>{
        saveAnswer();
        if(currentIndex < questions.length - 1){
            currentIndex++;
            renderQuestion(currentIndex);
        }
    });

    document.getElementById('submitBtn').addEventListener('click', ()=>{
        saveAnswer();
        fetch('/exam/api/submit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                testIdx: testIdx,
                answers: answers,
                startTime: startTime,
                endTime: new Date().toISOString()
            })
        }).then(res => res.json())
          .then(data => {
              if(data.success){
                  alert('시험 제출 완료!');
                  window.location.href = '/exam/result/' + data.resultIdx;
              } else {
                  alert('제출 실패: ' + data.message);
              }
          });
    });

    renderQuestion(currentIndex);
}

document.addEventListener('DOMContentLoaded', initQuiz);
