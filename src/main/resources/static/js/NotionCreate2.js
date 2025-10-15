document.addEventListener('DOMContentLoaded', () => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  const $slider = document.getElementById('buttonContainer');
  const $slideContainer = document.querySelector('.slide-container');
  const $leftArrow = document.querySelector('.arrow.left');
  const $rightArrow = document.querySelector('.arrow.right');
  const $temsetBtn = document.getElementById('temsetBtn');
  const $saveBtn = document.getElementById('saveBtn');

  let autoSlideInterval;

  const promptsData = window.prompts || [];

  // ====== 프롬프트 버튼 렌더링 ======
  promptsData.forEach((p, idx) => {
    const btn = document.createElement('button');
    btn.className = 'slide-btn';
    btn.innerHTML = `<img src="/images/Group.svg" alt="icon"><div class="kor">${p.title}</div>`;
    btn.dataset.index = idx;
    btn.addEventListener('click', () => {
      selectPrompt(idx);
      pauseSlider();
    });
    $slider.appendChild(btn);
  });

  const promptButtons = document.querySelectorAll('.slide-btn');

  // ====== Toast UI Editor 초기화 ======
  const editor = new toastui.Editor({
    el: document.querySelector('#editor'),
    height: '500px',
    initialEditType: 'markdown',
    previewStyle: 'vertical'
  });

  // ====== 무한 캐러셀 구현 ======
  $slider.innerHTML += $slider.innerHTML;
  const slideWidth = 180;
  let currentIndex = 0;
  let isPaused = false;
  let autoSlideFrame;
  

  // ⚙️ clone 대신 무한 루프 방식 (translateX 순환)
  function moveSlider(direction = 1) {
    currentIndex += direction;
    if (currentIndex >= promptsData.length) currentIndex = 0;
    if (currentIndex < 0) currentIndex = promptsData.length - 1;

    $slider.style.transition = 'transform 0.5s cubic-bezier(0.45, 0, 0.55, 1)';
    $slider.style.transform = `translateX(-${currentIndex * slideWidth}px)`;
  }

  function animateLoop() {
    if (!isPaused) moveSlider(1);
    autoSlideFrame = setTimeout(animateLoop, 2000);
  }

  function startAutoSlide() {
    clearTimeout(autoSlideFrame);
    animateLoop();
  }

  function pauseSlider() { isPaused = true; }
  function resumeSlider() { isPaused = false; }

  $leftArrow.addEventListener('click', () => { moveSlider(-1); pauseSlider(); });
  $rightArrow.addEventListener('click', () => { moveSlider(1); pauseSlider(); });
  $slideContainer.addEventListener('mouseenter', pauseSlider);
  $slideContainer.addEventListener('mouseleave', resumeSlider);

  startAutoSlide();

  // ====== 프롬프트 선택 ======
  let selectedPrompt = null;
  function selectPrompt(index) {
    promptButtons.forEach(btn => btn.classList.remove('active'));
    promptButtons[index].classList.add('active');
    selectedPrompt = promptsData[index];
    // ✅ 프롬프트 예시를 에디터에 삽입
    if (selectedPrompt.exampleOutput) {
      editor.setMarkdown(selectedPrompt.exampleOutput);
    } else if (selectedPrompt.content) {
      editor.setMarkdown(selectedPrompt.content);
    }
  }

  // ====== LLM 요청 ======
  $temsetBtn.addEventListener('click', async () => {
    if (!selectedPrompt) { alert('프롬프트를 선택하세요.'); return; }
    const content = editor.getMarkdown();
    if (!content.trim()) { alert('에디터에 내용이 없습니다.'); return; }

    try {
		$temsetBtn.textContent = '요약 중...';
      $temsetBtn.disabled = true;
      const res = await fetch('/notion/create-text', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {}) },
        body: JSON.stringify({ content, promptTitle: selectedPrompt.title })
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error || 'LLM 요청 실패');
      editor.setMarkdown(data.summary || '');
      alert('LLM 요약이 적용되었습니다.');
    } catch (err) {
      console.error(err);
      alert('LLM 요청 중 오류: ' + err.message);
    } finally {
      $temsetBtn.disabled = false;
    }
  });

  // ====== 저장 버튼 ======
  $saveBtn.addEventListener('click', async () => {
    const summary = editor.getMarkdown();
    if (!summary.trim()) { alert('저장할 내용이 없습니다.'); return; }

    try {
      $saveBtn.disabled = true;
      const res = await fetch('/notion/save-note', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {}) },
        body: JSON.stringify({ title: selectedPrompt ? selectedPrompt.title : '제목없음', summary, promptId: selectedPrompt ? selectedPrompt.promptId : 0 })
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error || '저장 실패');
      alert('저장 완료!');
    } catch (err) {
      console.error(err);
      alert('저장 중 오류: ' + err.message);
    } finally {
      $saveBtn.disabled = false;
    }
  });
});