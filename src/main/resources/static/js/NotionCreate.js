document.addEventListener("DOMContentLoaded", (format, data) => {
        // --- CSRF 토큰 설정 ---
        const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

        // --- 전역 변수 및 요소 가져오기1 ---

        const container = document.getElementById("buttonContainer");
        let selectedPromptTitle = "심플버전"; // 기본값


     	// ✅ Toast Editor 초기화 
     	const editor = new toastui.Editor({
     		el: document.querySelector('#editor'), 
     		height: '480px', 
     		initialEditType: 'markdown', 
     		previewStyle: 'vertical', 
     		language: 'ko', 
     		placeholder: 'AI가 생성한 요약본이 여기에 표시됩니다. 자유롭게 편집하세요!', 
     		usageStatistics: false }); 
        // 기본 프롬프트 예시 표시 
        const defaultPrompt = prompts.find(p => p.title === "심플버전"); 
        if (defaultPrompt && defaultPrompt.exampleOutput) { 
        	editor.setMarkdown(defaultPrompt.exampleOutput); }

        // --- 슬라이드 버튼 동적 생성 ---
        prompts.forEach(prompt => {
            const btn = document.createElement("div");
            btn.className = "slide-btn";
            btn.dataset.promptTitle = prompt.title;
            btn.dataset.exampleOutput = prompt.exampleOutput; // 'example_ouptput' 오타 수정
            btn.innerHTML = ` <img src="${image}" alt="${prompt.title}" class="slide-icon" />
            	  <span class="kor">${prompt.title}</span>`;

            // 기본 '심플버전' 선택
            if (prompt.title === selectedPromptTitle) {
                btn.classList.add('active');
            }

            btn.addEventListener('click', () => {
                document.querySelectorAll('.slide-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                selectedPromptTitle = btn.dataset.promptTitle;

                const scrollY = window.scrollY; // 현재 페이지 스크롤 저장
                editor.setMarkdown(btn.dataset.exampleOutput);
                setTimeout(() => {
                    window.scrollTo(0, scrollY); // 페이지 스크롤 원래 위치로 복원
                }, 0);
            });
            container.appendChild(btn);
        });

        // --- 이벤트 리스너 설정 ---
        const generateBtn = document.getElementById('generateBtn');
        const saveBtn = document.getElementById('saveBtn');

        generateBtn.addEventListener('click', handleGenerate);
        saveBtn.addEventListener('click', handleSave);

        // --- 기능 함수 ---
        async function handleGenerate() {
            const title = document.getElementById('inputTitle').value.trim();
            const content = document.getElementById('inputContent').value.trim();

            if (!title || !content) {
                alert('제목과 내용을 모두 입력해주세요.');
                return;
            }

            generateBtn.textContent = 'AI 요약 중...';
            generateBtn.disabled = true;

            try {
                const response = await fetch('/api/notion/generate-summary', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken
                    },
                    body: JSON.stringify({
                        content: content,
                        notionType: selectedPromptTitle
                    })
                });

                if (!response.ok) throw new Error('AI 요약 생성에 실패했습니다.');

                const result = await response.json();

                if (result.error) {
                    throw new Error(result.error);
                }

                document.getElementById('resultTitle').value = title;
                editor.setData(result.summary);

            } catch (error) {
                console.error('요약 생성 오류:', error);
                alert('요약 생성 중 오류가 발생했습니다: ' + error.message);
            } finally {
                generateBtn.textContent = '작성';
                generateBtn.disabled = false;
            }
        }

        async function handleSave() {
            const title = document.getElementById('resultTitle').value.trim();
            const content = editor.getData();

            if (!title || !content) {
                alert('저장할 제목과 내용이 없습니다.');
                return;
            }

            saveBtn.textContent = '저장 중...';
            saveBtn.disabled = true;

            try {
                const response = await fetch('/api/notes', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        [csrfHeader]: csrfToken
                    },
                    body: JSON.stringify({ title, content })
                });

                if (!response.ok) throw new Error('저장 실패');

                const result = await response.json();
                if (result.success) {
                    alert('노트가 성공적으로 저장되었습니다!');
                    window.location.href = '/notion/manage'; // 저장 후 관리 페이지로 이동
                } else {
                    throw new Error(result.message);
                }

            } catch (error) {
                console.error('저장 오류:', error);
                alert('저장 중 오류가 발생했습니다.');
            } finally {
                saveBtn.textContent = '저장';
                saveBtn.disabled = false;
            }
        }

        // --- 슬라이드 UI 로직 (기존 코드 유지) ---
        const slider = document.querySelector(".slide-container");
        const track = document.querySelector(".slider-wrapper");
        const leftBtn = document.querySelector(".arrow.left");
        const rightBtn = document.querySelector(".arrow.right");

        leftBtn.addEventListener("click", () => track.scrollBy({ left: -300, behavior: "smooth" }));
        rightBtn.addEventListener("click", () => track.scrollBy({ left: 300, behavior: "smooth" }));
        track.addEventListener("wheel", (e) => {
            e.preventDefault();
            track.scrollBy({ left: e.deltaY, behavior: "smooth" });
        });
        // 버튼 복제하여 무한루프 구현
	  	const originalButtons = Array.from(container.children);
	  	for (let i = 0; i < 3; i++) {
	  	  originalButtons.forEach(btn => container.appendChild(btn.cloneNode(true)));
	  	}
	  	// 자동 슬라이드 변수
	  	let scrollSpeed = 0.8;
	  	let isPaused = false;
	  	const originalSetWidth = originalButtons.reduce((acc, btn) => acc + btn.offsetWidth + 20, 0);
	  	// 자동 슬라이드 함수
	  	function animate() {
	  	  if (!isPaused) {
	  	    track.scrollLeft += scrollSpeed;
	  	    if (track.scrollLeft >= originalSetWidth) {
	  	      track.scrollLeft = 0;
	  	    }
	  	  }
	  	  requestAnimationFrame(animate);
	  	}
	  	animate();
	  	// 마우스 오버 시 일시정지
		slider.addEventListener("mouseenter", () => isPaused = true);
		slider.addEventListener("mouseleave", () => isPaused = false);
  	
  	  
  	
  	

		
    });