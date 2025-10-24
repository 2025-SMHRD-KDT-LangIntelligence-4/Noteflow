document.addEventListener('DOMContentLoaded', () => {
  // ==== CSRF ====
  const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  const withCsrf = (headers = {}) => { if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken; return headers; };

  // ==== DOM ====
  const $promptStage     = document.getElementById('nc-promptStage');
  const $inputStage      = document.getElementById('nc-inputStage');
  const $btnBack         = document.getElementById('nc-btnBack');

  const $buttonContainer = document.getElementById('nc-buttonContainer');
  const $leftArrow       = document.querySelector('.nc-left');
  const $rightArrow      = document.querySelector('.nc-right');
  const $promptViewerEl  = document.getElementById('nc-promptExampleViewer');

  const $selectedName    = document.getElementById('nc-selectedPromptName');
  const $titleInput      = document.getElementById('nc-ResultTitle');
  const $fileInput       = document.getElementById('nc-fileInput');
  const $fileUploadBtn   = document.getElementById('nc-file-upload');
  const $preMsg          = document.getElementById('nc-preMsg');
  const $temsetBtn       = document.getElementById('nc-temsetBtn');
  const $saveBtn         = document.getElementById('nc-saveBtn');
  const $resultBox       = document.getElementById('nc-resultBox');
  const $charCount       = document.getElementById('nc-charCount');
  const $tokenCount      = document.getElementById('nc-tokenCount');
  const $lengthHint      = document.getElementById('nc-lengthHint');
  
  // ✅ 버전 선택 버튼
  const $simpleBtn  = document.querySelector('.simpleBtn');
  const $normalBtn  = document.querySelector('.normalBtn');
  const $advanceBtn = document.querySelector('.advanceBtn');
  const $versionBtns = [$simpleBtn, $normalBtn, $advanceBtn];
  
  // ==== 상태/상수 ====
  const CARD_WIDTH = 170;
  const MAX_TOKENS = 7000;
  
  // ✅ 전체 프롬프트 & 필터링된 프롬프트 (1~16번만)
  const allPrompts = window.prompts || [];
  const displayPrompts = allPrompts.filter(p => p.promptId >= 1 && p.promptId <= 16);
  
  // ✅ 현재 선택된 버전 (기본값: 심플)
  let selectedVersion = 'simple';
  
  const state = {
    editor: null,
    editor2: null,
    viewer: null,
    isSummaryShown: false,
    isPaused: false,
    currentPosition: 0,
    cloneCount: 0,
    selectedPromptIdx: null,  // displayPrompts 배열의 인덱스 (0~15)
    peekChkIdx: null,         // 체크박스 미리보기
    truncated: false,
    blocked: false,
    sizeBytes: 0,
    mode: 'text',
    fileId: null,
    fileName: null,
    hasProcessedOnce: false,
    inputCache: '',           // 뒤로가기 캐시
    lastSummary: null,        // 마지막 요약 결과 저장
    lastWarn: null,           // 경고 메시지 저장
    isSaving: false
  };
  
  // ✅ 버전 버튼 클릭 이벤트
  $versionBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      $versionBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      selectedVersion = btn.dataset.version;
      console.log(`✅ 버전 선택: ${selectedVersion}`);
    });
  });
  
  // ✅ 버전에 따른 promptId offset 계산
  function getPromptIdOffset() {
    switch(selectedVersion) {
      case 'simple': return 32;   
      case 'normal': return 16;  
      case 'advance': return 0; 
      default: return 0;
    }
  }
  // ==== Toast UI: Editor & Viewer ====
  state.editor = new toastui.Editor({
    el: document.getElementById('nc-editor'),
    height: '500px',
    initialEditType: 'markdown',
    previewStyle: 'vertical',
    usageStatistics: false
  });
  state.editor.on('change', () => {
    updateCounters();
  });
  state.viewer = new toastui.Editor({
    el: $promptViewerEl,
    viewer: true,
    height: 'auto',
    initialEditType: 'wysiwyg',  // ✅ 추가
    usageStatistics: false
  });
  state.viewer.setMarkdown('**프롬프트가 여기에 표시됩니다.**\n\n카드 하단의 체크박스를 켜면 해당 프롬프트의 예시만 표시되고 슬라이드가 멈춥니다.');

  // ==== 유틸 ====
  const setMsg = (text, type='info') => { if (!$preMsg) return; $preMsg.textContent = text; $preMsg.style.color = type === 'error' ? 'red' : (type === 'success' ? 'green' : '#666'); };
  const fmtDate = () => { const d=new Date(); return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`; };
  const safeJson = async (res) => { const ct=res.headers.get('content-type')||''; if (!ct.includes('application/json')) { const body=await res.text(); throw new Error(`JSON이 아닌 응답입니다 (status=${res.status}, ct=${ct})\n${body.slice(0,200)}`); } return res.json(); };
  const estimateTokens = (text) => {
    if (!text) return 0;

    const koreanChars = (text.match(/[\u3131-\uD79D]/g) || []).length;
    const englishWords = (text.match(/[a-zA-Z]+/g) || []).length;
    const otherChars = text.length - koreanChars - englishWords;

    return Math.ceil(koreanChars * 2 + englishWords * 1 + otherChars * 0.5);
  };
  // ==== 카운터/버튼 ====
  function updateCounters() {
    const md = state.editor.getMarkdown();
    const len = md.trim().length;
    const tokens = estimateTokens(md); // ✅ 토큰 계산

    $charCount.textContent = len;
    $tokenCount.textContent = tokens; // ✅ 토큰 표시

    let hint = '', allowText = true;

    if (tokens === 0) {
      hint = '내용을 입력하세요';
      allowText = false;
    } else if (tokens < 50) {
      hint = '최소 50토큰 이상 입력해주세요';
      allowText = false;
    } else if (tokens < 150) {
      hint = '150토큰 이상 권장';
    } else if (tokens > MAX_TOKENS) {
      hint = `최대 ${MAX_TOKENS}토큰 초과!`;
      allowText = false;
      $tokenCount.style.color = 'red'; // ✅ 빨간색으로 표시
    } else {
      hint = '';
      $tokenCount.style.color = ''; // ✅ 기본 색상
    }

    $lengthHint.textContent = hint;

    const promptSelected = state.selectedPromptIdx !== null;
    const allowSave = tokens > 0 && tokens <= MAX_TOKENS;
	const allowSummary =state.mode === 'file' ? !!state.fileId : (tokens >= 50 && tokens <= MAX_TOKENS);
	if ($temsetBtn) $temsetBtn.disabled = !promptSelected || !allowSummary;

    if ($saveBtn) $saveBtn.disabled = !allowSave;
  }
  state.editor.on('change', updateCounters);

  // ==== 슬라이드: 카드 생성 ====
  function makeCard(p, idx) {
    const card = document.createElement('div');
    card.className = 'nc-slide-item';
    card.dataset.index = idx;
    card.style.width = CARD_WIDTH + 'px';

    const title = document.createElement('div');
    title.className = 'nc-item-title';
    title.title = p.title || '제목 없음';
    title.textContent = p.title || '제목 없음';
    const actions = document.createElement('div');
    actions.className = 'nc-item-actions';

    // 체크박스 (미리보기/정지 토글)
    const label = document.createElement('label');
    label.className = 'nc-peek-label';
    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.className = 'nc-peek-check';
    chk.dataset.index = idx;
    label.appendChild(chk);
    label.appendChild(document.createTextNode(' 이 프롬프트 예시 자세히 보기'));

    // 확정 선택 버튼
    const btn = document.createElement('button');
    btn.className = 'nc-btn nc-primary';
    btn.textContent = '프롬프트 선택';
    btn.addEventListener('click', () => finalizePromptSelection(idx));

    actions.appendChild(label);
    actions.appendChild(btn);
	// 이미지
	if (p.imageUrl) {
	    const img = document.createElement('img');
	    img.src = p.imageUrl;
	    img.alt = p.title || '프롬프트 이미지';
	    img.className = 'nc-item-image';
	    img.style.width = '100%'; // 카드 전체 폭
	    img.style.height = '120px'; // 원하는 높이
	    img.style.objectFit = 'cover';
		card.appendChild(title);
	    card.appendChild(img);
	}else{
		card.appendChild(title);

	}
	
    card.appendChild(actions);

    // 체크박스 동작
    chk.addEventListener('change', () => {
      if (chk.checked) {
        document.querySelectorAll('.nc-peek-check').forEach(c => { if (c !== chk) c.checked = false; });
        state.peekChkIdx = idx;
        pauseSlider();
        const md = p.exampleOutput || p.content || '';
        state.viewer.setMarkdown(md);
        document.querySelectorAll('.nc-slide-item').forEach(el => el.classList.remove('nc-selected'));
        card.classList.add('nc-selected');
      } else {
        state.peekChkIdx = null;
        card.classList.remove('nc-selected');
        if (state.selectedPromptIdx !== null) {
          const sp = prompts[state.selectedPromptIdx];
          const md = sp?.exampleOutput || sp?.content || '';
          state.viewer.setMarkdown(md);
        } else {
          state.viewer.setMarkdown('**프롬프트가 여기에 표시됩니다.**\n\n카드 하단의 체크박스를 켜면 해당 프롬프트의 예시만 표시되고 슬라이드가 멈춥니다.');
        }
        resumeSlider();
      }
    });

    return card;
  }

  // 복제 카드 이벤트 연결(무한 캐러셀용)
  function wireCardEvents(cardEl) {
    const idx = parseInt(cardEl.dataset.index);
    const p = prompts[idx];
    const chk = cardEl.querySelector('.nc-peek-check');
    const btn = cardEl.querySelector('.nc-btn.nc-primary');

    if (chk) {
      chk.addEventListener('change', () => {
        if (chk.checked) {
          document.querySelectorAll('.nc-peek-check').forEach(c => { if (c !== chk) c.checked = false; });
          state.peekChkIdx = idx;
          pauseSlider();
          const md = p.exampleOutput || p.content || '';
          state.viewer.setMarkdown(md);
          document.querySelectorAll('.nc-slide-item').forEach(el => el.classList.remove('nc-selected'));
          cardEl.classList.add('nc-selected');
        } else {
          state.peekChkIdx = null;
          cardEl.classList.remove('nc-selected');
          if (state.selectedPromptIdx !== null) {
            const sp = prompts[state.selectedPromptIdx];
            const md = sp?.exampleOutput || sp?.content || '';
            state.viewer.setMarkdown(md);
          } else {
            state.viewer.setMarkdown('**프롬프트가 여기에 표시됩니다.**\n\n카드 하단의 체크박스를 켜면 해당 프롬프트의 예시만 표시되고 슬라이드가 멈춥니다.');
          }
          resumeSlider();
        }
      });
    }
    if (btn) {
      btn.addEventListener('click', () => finalizePromptSelection(idx));
    }
  }

  // 렌더 & 무한 캐러셀 복제
  function renderSlider() {
      $buttonContainer.innerHTML = '';
      displayPrompts.forEach((p, idx) => $buttonContainer.appendChild(makeCard(p, idx)));

      const wrapperWidth = document.querySelector('.nc-slide-container').clientWidth;
      state.cloneCount = Math.ceil(wrapperWidth / CARD_WIDTH);
      const originals = Array.from($buttonContainer.children);

      // 앞쪽 복제
      for (let i = displayPrompts.length - state.cloneCount; i < displayPrompts.length; i++) {
        const clone = originals[i].cloneNode(true);
        wireCardEvents(clone);
        $buttonContainer.insertBefore(clone, $buttonContainer.firstChild);
      }

      // 뒤쪽 복제
      for (let i = 0; i < state.cloneCount; i++) {
        const clone = originals[i].cloneNode(true);
        wireCardEvents(clone);
        $buttonContainer.appendChild(clone);
      }

      state.currentPosition = state.cloneCount * CARD_WIDTH;
      $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
      startAutoSlide();
    }

  // ==== 슬라이드 이동 ====
  let animId = null, lastTime = Date.now();
  function startAutoSlide() {
    cancelAnimationFrame(animId);
    lastTime = Date.now();
    smoothSlide();
  }
  function smoothSlide() {
    if (!state.isPaused) {
      const now = Date.now();
      const delta = now - lastTime;
      if (delta >= 20) {
        state.currentPosition += 0.6;
        $buttonContainer.style.transition = 'none';
        $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
        const maxPos = (prompts.length + state.cloneCount) * CARD_WIDTH;
        if (state.currentPosition >= maxPos) {
          state.currentPosition = state.cloneCount * CARD_WIDTH;
          $buttonContainer.style.transition = 'none';
          $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
        }
        lastTime = now;
      }
    }
    animId = requestAnimationFrame(smoothSlide);
  }
  function pauseSlider() { state.isPaused = true; }
  function resumeSlider() { state.isPaused = false; lastTime = Date.now(); }

  // 화살표 클릭
  $leftArrow.addEventListener('click', () => {
    state.currentPosition -= CARD_WIDTH;
    $buttonContainer.style.transition = 'transform 0.5s ease';
    $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
    pauseSlider();
    setTimeout(() => {
      resumeSlider();
      if (state.currentPosition < state.cloneCount * CARD_WIDTH) {
        state.currentPosition = (prompts.length + state.cloneCount - 1) * CARD_WIDTH;
        $buttonContainer.style.transition = 'none';
        $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
      }
    }, 500);
  });
  $rightArrow.addEventListener('click', () => {
    state.currentPosition += CARD_WIDTH;
    $buttonContainer.style.transition = 'transform 0.5s ease';
    $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
    pauseSlider();
    setTimeout(() => {
      resumeSlider();
      const maxPos = (prompts.length + state.cloneCount) * CARD_WIDTH;
      if (state.currentPosition >= maxPos) {
        state.currentPosition = state.cloneCount * CARD_WIDTH;
        $buttonContainer.style.transition = 'none';
        $buttonContainer.style.transform = `translateX(-${state.currentPosition}px)`;
      }
    }, 500);
  });

  // ==== ✅ 프롬프트 확정 선택 → [입력 단계]로 전환 ====
  function finalizePromptSelection(index) {
      state.selectedPromptIdx = index;
      const p = displayPrompts[index];
      
      $selectedName.textContent = `선택한 프롬프트: ${p.title}`;
      state.inputCache = state.editor.getMarkdown();
      
      $promptStage.style.display = 'none';
      $inputStage.style.display = 'flex';
      
      document.querySelectorAll('.nc-peek-check').forEach(c => c.checked = false);
      state.peekChkIdx = null;
      resumeSlider();
      
      if (state.inputCache) state.editor.setMarkdown(state.inputCache);
      updateCounters();
    }

  // 단계 복원 ====
  $btnBack.addEventListener('click', () => {
    // ✅ 프롬프트 선택 화면으로만 복귀
    state.inputCache = state.editor.getMarkdown();
    $inputStage.style.display = 'none';
    $promptStage.style.display = 'block';

    // 요약 결과 초기화
    $resultBox.innerHTML = '';
    state.isSummaryShown = false;

    if (state.editor2) {
      state.editor2.destroy();
      state.editor2 = null;
    }

    showEditorArea();

    resumeSlider();

    if (state.selectedPromptIdx !== null) {
      const sp = prompts[state.selectedPromptIdx];
      const md = sp?.exampleOutput || sp?.content || '';
      state.viewer.setMarkdown(md);
    } else {
      state.viewer.setMarkdown('**프롬프트가 여기에 표시됩니다.**\n\n카드 하단의 체크박스를 켜면 해당 프롬프트의 예시만 표시되고 슬라이드가 멈춥니다.');
    }
  });

  // ==== 파일 업로드 → preview-meta → 정책 ====
  $fileUploadBtn.addEventListener('click', () => $fileInput.click());
  $fileInput.addEventListener('change', async () => {
    const file = $fileInput.files?.[0];
    if (!file) return;

    // ✅ 화이트리스트: 텍스트 추출 가능한 파일만 허용
    const allowedExtensions = [
      // 문서 (간단한 것만)
      '.txt', '.md', '.markdown',
      '.pdf','.hwp',  // PDF는 pdfbox로 처리

      // 코드
      '.java', '.js', '.py', '.cpp', '.c', '.cs', '.php', '.rb', '.go',
      '.html', '.css', '.xml', '.json', '.yaml', '.yml',
      '.sql', '.sh', '.bat', '.ps1',

      // CSV만 (엑셀 제외)
      '.csv',

      // 기타
      '.rtf', '.log'
    ];

    const fileName = file.name.toLowerCase();
    const isAllowed = allowedExtensions.some(ext => fileName.endsWith(ext));

    if (!isAllowed) {
      const extMatch = fileName.match(/\.([^.]+)$/);
      const currentExt = extMatch ? extMatch[0] : '(확장자 없음)';

      alert(`⚠️ 지원하지 않는 파일 형식입니다: ${currentExt}\n\n지원 형식:\n• 문서: .txt, .pdf, .docx, .hwp 등\n• 코드: .java, .js, .py, .cpp 등\n`);
      $fileInput.value = ''; // ✅ 선택 초기화
      return;
    }

    setMsg('');

    const form = new FormData();
    form.append('file', file);

    try {
      const res1 = await fetch('/api/files/upload', {
        method: 'POST',
        body: form,
        headers: withCsrf({ 'Accept': 'application/json' }),
        credentials: 'same-origin'
      });

      const data1 = await safeJson(res1);

      if (!res1.ok || !data1.success) {
        throw new Error(`${data1.message} (status=${res1.status})`);
      }

      state.fileId = data1.gridfsId || data1.id;
      state.fileName = file.name;

      if (!$titleInput.value.trim()) {
        const baseName = file.name.replace(/\.[^.]+$/, '');
        $titleInput.value = `${fmtDate()}_${baseName}`;
      }

      if (state.fileId) {
        try {
          const res2 = await fetch(`/api/files/preview-meta/${encodeURIComponent(state.fileId)}`, {
            method: 'GET',
            headers: withCsrf({ 'Accept': 'application/json' }),
            credentials: 'same-origin'
          });

          const meta = await safeJson(res2);

          if (!res2.ok || !meta.success) {
            throw new Error(`${meta.message} (status=${res2.status})`);
          }

          const { text, truncated, blocked, sizeBytes } = meta;

          state.truncated = !!truncated;
          state.blocked = !!blocked;
          state.sizeBytes = Number(sizeBytes || 0);

          if (state.blocked) {
            state.mode = 'file';
            setMsg(`❌ 차단됨.`, 'error');
            state.editor.setMarkdown('');

          } else if (!text || text.trim().length === 0) {
            state.mode = 'file';
            setMsg(`⚠ 텍스트 추출 실패. 파일만 저장됩니다.`, 'info');
            state.editor.setMarkdown('');

          } else if (state.truncated) {
            state.mode = 'file';
            setMsg(`⚠ 일부만 표시. 요약은 전체 대상.`, 'info');
            state.editor.setMarkdown(text);

          } else {
            state.mode = 'text';
            setMsg('✅ 업로드 완료', 'success');
            state.editor.setMarkdown(text);
          }

          updateCounters();

        } catch (e2) {
          console.error(e2);
          setMsg(e2.message, 'error');
        }
      }

    } catch (err) {
      console.error(err);
      setMsg(err.message, 'error');
    }
  });

  // ==== LLM 요청 ====
  function setLoading(isLoading, message='요약 중...') {
    document.querySelectorAll('button').forEach(b => b.disabled = isLoading);
    const overlay = document.getElementById('nc-loadingOverlay');
    if (overlay) { overlay.style.display = isLoading ? 'flex' : 'none'; overlay.textContent = message; }
  }
  async function requestTextSummary(contentToSend, promptId) {
      const res = await fetch('/notion/create-text', {
        method: 'POST',
        headers: {
          ...withCsrf(),
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          content: contentToSend,
          promptId: promptId  // ✅ promptId 전달
        })
      });
      
      const data = await res.json();
      if (!data.success) throw new Error(data.error || '요약 실패');
      return data;
    }
	async function requestFileSummaryById(fileId, promptId) {
	    const res = await fetch('/notion/create-by-id', {
	      method: 'POST',
	      headers: {
	        ...withCsrf(),
	        'Content-Type': 'application/json'
	      },
	      body: JSON.stringify({
	        fileId: fileId,
	        promptId: promptId  // ✅ promptId 전달
	      })
	    });
	    
	    const data = await res.json();
	    return data;
	  }

  // 요약하기
  $temsetBtn.addEventListener('click', async () => {
      if (state.selectedPromptIdx === null) {
        alert('프롬프트를 먼저 선택해주세요.');
        return;
      }

      // 선택한 프롬프트 (1~16번 중 하나)
      const selectedPrompt = displayPrompts[state.selectedPromptIdx];
      
      // ✅ 버전에 따라 offset 추가
      const offset = getPromptIdOffset();
      const actualPromptId = selectedPrompt.promptId + offset;
      
      console.log(`🔥 선택 프롬프트ID: ${selectedPrompt.promptId}, 버전: ${selectedVersion}, 실제 사용: ${actualPromptId}`);

      try {
        setLoading(true, '요약 중입니다...');
        let response;

        if (state.mode === 'file') {
          if (!state.fileId) {
            alert('파일 ID가 없습니다.');
            setLoading(false);
            return;
          }
          
          // ✅ actualPromptId 전달
          response = await requestFileSummaryById(state.fileId, actualPromptId);
          
          if (response.success) {
            state.lastSummary = response.summary;
            state.lastWarn = response.message;
            showSummaryResult(state.lastSummary, state.lastWarn);
          } else {
            const msg = response.error || response.message || '요약 실패';
            $resultBox.innerHTML = `<div class="nc-error">${msg}</div>`;
          }
        } else {
          // ✅ 텍스트 모드
          let contentToSend;
          
          if (!state.hasProcessedOnce) {
            const promptContent = selectedPrompt.content || '';
            const md = state.editor.getMarkdown().trim();
            
            if (!md) {
              alert('내용을 입력해주세요.');
              setLoading(false);
              return;
            }
            
            contentToSend = md + '\n\n' + promptContent;
          } else {
            contentToSend = state.editor.getMarkdown().trim();
            
            if (!contentToSend) {
              alert('내용을 입력해주세요.');
              setLoading(false);
              return;
            }
          }

          // ✅ actualPromptId 전달
          response = await requestTextSummary(contentToSend, actualPromptId);
          
          state.lastSummary = response.summary;
          state.lastWarn = response.warn;
          showSummaryResult(state.lastSummary, state.lastWarn);
          state.hasProcessedOnce = true;
        }

        alert('요약이 완료되었습니다!');
        updateCounters();
      } catch (err) {
        console.error(err);
        alert(`오류: ${err.message}`);
      } finally {
        setLoading(false);
      }
    });

// ✅ 요약 결과 표시 
  function showSummaryResult(summary, warnMsg) {
    hideEditorArea();

    const warn = warnMsg ? `<div class="nc-warn">${warnMsg}</div>` : '';

    // ✅ 별도 ID로 버튼 생성
    const backBtn = `<div style="text-align:right; margin-bottom:10px; margin-top:10px;">
    <button id="nc-backToInput" class="nc-btn nc-secondary">← 입력 화면으로 돌아가기</button>
  </div>`;

    $resultBox.innerHTML = backBtn + warn + `<div id="nc-editor2"></div>`;
    state.isSummaryShown = true;

    // ✅ 입력 화면으로만 복귀
    document.getElementById('nc-backToInput').addEventListener('click', () => {
      showEditorArea();

      if (state.editor2) {
        state.editor2.destroy();
        state.editor2 = null;
      }

      $resultBox.innerHTML = '';
      state.isSummaryShown = false;
    });

    // ✅ editor2 생성
    setTimeout(() => {
      const editor2El = document.getElementById('nc-editor2');
      if (!editor2El) {
        console.error('nc-editor2 요소를 찾을 수 없습니다!');
        return;
      }

      if (state.editor2) {
        state.editor2.destroy();
        state.editor2 = null;
      }

      state.editor2 = new toastui.Editor({
        el: editor2El,
        height: '600px',
        initialEditType: 'wysiwyg',
        previewStyle: 'vertical',
        usageStatistics: false
      });

      state.editor2.setMarkdown(summary);
    }, 0);
  }


  // ==== hideEditorArea  ====
  function hideEditorArea() {

    // 1. label 태그 "입력/편집" 숨기기
    const labels = document.querySelectorAll('.nc-text-input label');
    labels.forEach(label => {
      if (label.textContent.trim() === '입력/편집') {
        label.style.display = 'none';
      }
    });

    // 2. 에디터 본체 숨기기
    const editor = document.getElementById('nc-editor');
    if (editor) {
      editor.style.display = 'none';
    }

    // 3. 글자수/토큰
    const counters = document.querySelector('.nc-counters');
    if (counters) {
      counters.style.display = 'none';
    }

    // 4. ✅ 파일 업로드 영역 (정확한 클래스명)
    const fileSection = document.querySelector('.nc-file-section');
    if (fileSection) {
      fileSection.style.display = 'none';
    } else {
      console.error('.nc-file-section 찾을 수 없음');
    }
  }

  function showEditorArea() {

    const labels = document.querySelectorAll('.nc-text-input label');
    labels.forEach(label => {
      if (label.textContent.trim() === '입력/편집') {
        label.style.display = 'block';
      }
    });

    const editor = document.getElementById('nc-editor');
    if (editor) editor.style.display = 'block';

    const counters = document.querySelector('.nc-counters');
    if (counters) counters.style.display = 'flex';

    const fileSection = document.querySelector('.nc-file-section');
    if (fileSection) fileSection.style.display = 'block';
  }

  // ✅ 저장 버튼
  $saveBtn.addEventListener('click', async () => {
    if (state.isSaving) return;

    const title = $titleInput.value.trim();
    if (!title) {
      alert('제목을 입력해주세요.');
      return;
    }

    // ✅ 실제 사용된 promptId 계산
    let finalPromptId = null;
    if (state.selectedPromptIdx !== null) {
      const selectedPrompt = displayPrompts[state.selectedPromptIdx];
      const offset = getPromptIdOffset();
      finalPromptId = selectedPrompt.promptId + offset;
    }

    let content;
    let originalContent;

    if (state.isSummaryShown && state.editor2) {
      content = state.editor2.getMarkdown();
      originalContent = state.editor.getMarkdown();
    } else if (state.editor) {
      originalContent = state.editor.getMarkdown();
      content = originalContent;
    }

    if (!content.trim()) {
      alert('내용을 입력해주세요.');
      return;
    }

    state.isSaving = true;
    $saveBtn.disabled = true;
    $saveBtn.textContent = '저장 중...';

    try {
      const res = await fetch('/notion/save-note', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
        },
        credentials: 'same-origin',
        body: JSON.stringify({
          title: title,
          summary: content,
          originalContent: originalContent,
          promptId: finalPromptId,  // ✅ 실제 사용된 promptId 저장
          gridfsId: state.fileId
        })
      });

      const data = await res.json();
      
      if (!data.success) {
        throw new Error(data.error || '저장 실패');
      }

      sessionStorage.setItem('noteId', data.noteId);
      sessionStorage.setItem('keywords', (data.keywords || []).join(', '));
      sessionStorage.setItem('categoryPath', data.categoryPath || '');
      sessionStorage.setItem('folderId', data.folderId || '');
      
      window.location.href = '/notion/complete';
    } catch (err) {
      console.error('저장 오류:', err);
      alert(`저장 실패: ${err.message}`);
    } finally {
      state.isSaving = false;
      $saveBtn.disabled = false;
      $saveBtn.textContent = '저장하기';
    }
  });

  // 초기 렌더/카운터
  renderSlider();
  updateCounters();
});
