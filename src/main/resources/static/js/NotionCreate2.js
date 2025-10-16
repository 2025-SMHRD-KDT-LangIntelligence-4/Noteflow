document.addEventListener('DOMContentLoaded', () => {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    const $slider = document.getElementById('buttonContainer');
    const $slideContainer = document.querySelector('.slide-container');
    const $leftArrow = document.querySelector('.arrow.left');
    const $rightArrow = document.querySelector('.arrow.right');
    const $temsetBtn = document.getElementById('temsetBtn');
    const $saveBtn = document.getElementById('saveBtn');
    const overlay = document.getElementById('loadingOverlay');

    const promptsData = window.prompts || [];

    // ====== PreCreate에서 전달된 데이터 읽기 ======
    const preData = sessionStorage.getItem('notion.precreate');
    const pre = preData ? JSON.parse(preData) : { title: '', content: '' };
    let originalContent = pre.content || '';

    console.log('PreCreate 데이터:', pre); // 디버깅용

    // ====== Toast UI Editor 초기화 ======
    const editor = new toastui.Editor({
        el: document.querySelector('#editor'),
        height: '500px',
        initialEditType: 'markdown',
        previewStyle: 'vertical'
    });

    // ====== 제목 초기값 설정 ======
    if (pre.title) {
        document.getElementById('ResultTitle').value = pre.title;
    }

    // ====== 로딩 상태 관리 ======
    function setLoading(isLoading, message = '작동 중...') {
        document.querySelectorAll('button').forEach(b => b.disabled = isLoading);
        overlay.style.display = isLoading ? 'flex' : 'none';
        overlay.textContent = message;
        
        if (isLoading) {
            pauseSlider();
        } else {
            resumeSlider();
        }
    }

	// ====== 프롬프트 버튼 렌더링 ======
	promptsData.forEach((p, idx) => {
	  const btn = document.createElement('button');
	  btn.className = 'slide-btn';
	  btn.innerHTML = `<img src="/images/Group.svg"><div class="kor">${p.title}</div>`;
	  btn.dataset.index = idx;
	  btn.addEventListener('click', () => {
	    selectPrompt(idx);
	    pauseSlider();
	    setTimeout(resumeSlider, 2000);
	  });
	  $slider.appendChild(btn);
	});

	// ====== 동적으로 보이는 버튼 개수 계산 ======
	const slideWidth = 180;
	const wrapperWidth = $slideContainer.clientWidth;
	const cloneCount = Math.ceil(wrapperWidth / slideWidth);

	// 원본 버튼 리스트
	const originalButtons = Array.from($slider.children);

	// ====== 앞쪽에 마지막 cloneCount개 복제 ======
	for (let i = promptsData.length - cloneCount; i < promptsData.length; i++) {
	  const clone = originalButtons[i].cloneNode(true);
	  clone.dataset.clone = 'before';
	  clone.dataset.index = i;
	  clone.addEventListener('click', () => {
	    selectPrompt(i);
	    pauseSlider();
	    setTimeout(resumeSlider, 2000);
	  });
	  $slider.insertBefore(clone, $slider.firstChild);
	}

	// ====== 뒤쪽에 처음 cloneCount개 복제 ======
	for (let i = 0; i < cloneCount; i++) {
	  const clone = originalButtons[i].cloneNode(true);
	  clone.dataset.clone = 'after';
	  clone.dataset.index = i;
	  clone.addEventListener('click', () => {
	    selectPrompt(i);
	    pauseSlider();
	    setTimeout(resumeSlider, 2000);
	  });
	  $slider.appendChild(clone);
	}

	const allButtons = $slider.querySelectorAll('.slide-btn');

	// ====== 무한 캐러셀 구현 ======
	let currentPosition = cloneCount * slideWidth;
	let isPaused = false;
	let animId, lastTime = Date.now();

	// 초기 위치
	$slider.style.transform = `translateX(-${currentPosition}px)`;

	function smoothSlide() {
	  if (!isPaused) {
	    const now = Date.now();
	    const delta = now - lastTime;
	    if (delta >= 20) {
	      currentPosition += 0.5;
	      $slider.style.transition = 'none';
	      $slider.style.transform = `translateX(-${currentPosition}px)`;
	      const maxPos = (promptsData.length + cloneCount) * slideWidth;
	      if (currentPosition >= maxPos) {
	        currentPosition = cloneCount * slideWidth;
	      }
	      lastTime = now;
	    }
	  }
	  animId = requestAnimationFrame(smoothSlide);
	}

	function pauseSlider() { isPaused = true; }
	function resumeSlider() { isPaused = false; lastTime = Date.now(); }

	function startAutoSlide() {
	  cancelAnimationFrame(animId);
	  lastTime = Date.now();
	  smoothSlide();
	}

	// ====== 화살표 & 마우스 이벤트 ======
	$leftArrow.addEventListener('click', () => {
	  currentPosition -= slideWidth;
	  $slider.style.transition = 'transform 0.5s ease';
	  $slider.style.transform = `translateX(-${currentPosition}px)`;
	  pauseSlider();
	  setTimeout(() => {
	    resumeSlider();
	    if (currentPosition < cloneCount * slideWidth) {
	      currentPosition = (promptsData.length + cloneCount - 1) * slideWidth;
	      $slider.style.transition = 'none';
	      $slider.style.transform = `translateX(-${currentPosition}px)`;
	    }
	  }, 500);
	});
	$rightArrow.addEventListener('click', () => {
	  currentPosition += slideWidth;
	  $slider.style.transition = 'transform 0.5s ease';
	  $slider.style.transform = `translateX(-${currentPosition}px)`;
	  pauseSlider();
	  setTimeout(() => {
	    resumeSlider();
	    const maxPos = (promptsData.length + cloneCount) * slideWidth;
	    if (currentPosition >= maxPos) {
	      currentPosition = cloneCount * slideWidth;
	      $slider.style.transition = 'none';
	      $slider.style.transform = `translateX(-${currentPosition}px)`;
	    }
	  }, 500);
	});
	$slideContainer.addEventListener('mouseenter', pauseSlider);
	$slideContainer.addEventListener('mouseleave', resumeSlider);
	allButtons.forEach(btn => {
	  btn.addEventListener('mouseenter', pauseSlider);
	  btn.addEventListener('mouseleave', () => setTimeout(resumeSlider, 1000));
	});

	startAutoSlide();
    // ====== 프롬프트 선택 ======
    let selectedPrompt = null;
    let hasProcessedOnce = false;

    function selectPrompt(index) {
        // 모든 버튼에서 active 제거
        allButtons.forEach(btn => btn.classList.remove('active'));
        
        // 같은 인덱스의 모든 버튼에 active 추가 (원본 + 복제본)
        allButtons.forEach(btn => {
            const btnIndex = parseInt(btn.dataset.index) || parseInt(btn.dataset.originalIndex);
            if (btnIndex === index) {
                btn.classList.add('active');
            }
        });

        selectedPrompt = promptsData[index];
        
        console.log('선택된 프롬프트:', selectedPrompt); // 디버깅용
        
        // 에디터에는 프롬프트 예시만 표시 (처리 전에만)
        if (!hasProcessedOnce) {
            const template = selectedPrompt.exampleOutput || selectedPrompt.content || '';
            editor.setMarkdown(template);
        }
    }

    // ====== LLM 요청 공통 함수 ======
    function makeLLMRequest(contentToSend, promptTitle) {
        console.log('LLM 요청 내용:', contentToSend); // 디버깅용
        
        return fetch('/notion/create-text', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
            },
            body: JSON.stringify({
                content: contentToSend,
                promptTitle: promptTitle
            })
        })
        .then(res => res.json())
        .then(data => {
            if (!data.success) throw new Error(data.error || 'LLM 요청 실패');
            return data.summary || '';
        });
    }

    // ====== 확정하기 버튼 (LLM 요청) ======
    $temsetBtn.addEventListener('click', async () => {
        if (!selectedPrompt) {
            alert('프롬프트를 선택하세요.');
            return;
        }

        let contentToSend = '';

        if (!hasProcessedOnce) {
            // 최초 요청: PreCreate의 원본 내용 + 선택된 프롬프트
            if (!originalContent.trim()) {
                alert('처리할 원본 내용이 없습니다.');
                return;
            }
            
            const promptContent = selectedPrompt.content || '';
            contentToSend = `${originalContent}\n\n프롬프트: ${promptContent}\n\n`;
        } else {
            // 재요청: 에디터의 현재 내용 사용
            contentToSend = editor.getMarkdown().trim();
            if (!contentToSend) {
                alert('요약할 내용이 없습니다.');
                return;
            }
            ;
        }

        try {
            setLoading(true, '일하는 중....');

            const summary = await makeLLMRequest(contentToSend, selectedPrompt.title);
            
            // 키워드 부분 제거하고 요약만 표시
            const cleanSummary = summary
            editor.setMarkdown(cleanSummary);
            
            hasProcessedOnce = true;
            alert('요약이 완료되었습니다!');

        } catch (err) {
            console.error(err);
            alert('요청 중 오류: ' + err.message);
        } finally {
            setLoading(false);
        }
    });

    // ====== 저장 버튼 ======

    $saveBtn.addEventListener('click', async () => {
        const titleInput = document.getElementById('ResultTitle');
        const title = titleInput.value.trim();
        if (!title) {
            alert('제목을 입력하세요.');
            return;
        }

        const summary = editor.getMarkdown();
        if (!summary.trim()) {
            alert('저장할 내용이 없습니다.');
            return;
        }

        try {
            setLoading(true, '저장 중...');

            const res = await fetch('/notion/save-note', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({
                    title: title,
                    summary: summary,
                    promptId: selectedPrompt ? selectedPrompt.promptId : 0
                })
            });

            const data = await res.json();
            if (!data.success) throw new Error(data.error || '저장 실패');

            // ✅ 세션스토리지에 데이터 저장
            sessionStorage.setItem('noteTitle', title);
            sessionStorage.setItem('noteContent', summary);
            sessionStorage.setItem('keywords', (data.keywords || []).join(', '));
            sessionStorage.setItem('categoryPath', data.categoryPath || '');
            sessionStorage.setItem('folderId', data.folderId || '');

            // ✅ 완료 페이지로 이동
            window.location.href = '/notion/complete';

        } catch (err) {
            console.error(err);
            alert('저장 중 오류: ' + err.message);
        } finally {
            setLoading(false);
        }
    });

});