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
  const $filePreview     = document.getElementById('nc-filePreview');
  const $preMsg          = document.getElementById('nc-preMsg');
  const $temsetBtn       = document.getElementById('nc-temsetBtn');
  const $saveBtn         = document.getElementById('nc-saveBtn');
  const $resultBox       = document.getElementById('nc-resultBox');
  const $charCount       = document.getElementById('nc-charCount');
  const $tokenCount      = document.getElementById('nc-tokenCount');
  const $lengthHint      = document.getElementById('nc-lengthHint');

  // ==== 상태/상수 ====
  const CARD_WIDTH = 240;
  const MAX_LEN    = 5000;
  const prompts    = window.prompts || [];

  const state = {
    editor: null, viewer: null,
    isPaused: false,
    currentPosition: 0,
    cloneCount: 0,
    selectedPromptIdx: null,     // 확정 선택
    peekChkIdx: null,            // 체크박스 미리보기
    truncated: false, blocked: false, sizeBytes: 0,
    mode: 'text', fileId: null, fileName: null,
    hasProcessedOnce: false,
    inputCache: ''               // 뒤로가기 캐시
  };

  // ==== Toast UI: Editor & Viewer ====
  state.editor = new toastui.Editor({
    el: document.getElementById('nc-editor'),
    height: '500px',
    initialEditType: 'markdown',
    previewStyle: 'vertical',
    usageStatistics: false
  });
  state.viewer = new toastui.Editor({
    el: $promptViewerEl,
    viewer: true,
    height: 'auto',
    usageStatistics: false
  });
  state.viewer.setMarkdown('**프롬프트가 여기에 표시됩니다.**\n\n카드 하단의 체크박스를 켜면 해당 프롬프트의 예시만 표시되고 슬라이드가 멈춥니다.');

  // ==== 유틸 ====
  const setMsg = (text, type='info') => { if (!$preMsg) return; $preMsg.textContent = text; $preMsg.style.color = type === 'error' ? 'red' : (type === 'success' ? 'green' : '#666'); };
  const fmtDate = () => { const d=new Date(); return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`; };
  const safeJson = async (res) => { const ct=res.headers.get('content-type')||''; if (!ct.includes('application/json')) { const body=await res.text(); throw new Error(`JSON이 아닌 응답입니다 (status=${res.status}, ct=${ct})\n${body.slice(0,200)}`); } return res.json(); };
  const estimateTokens = (s) => Math.round((s||'').length/2);
  const escapeHtml = (s) => (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

  // ==== 카운터/버튼 ====
  function updateCounters() {
    const md = state.editor.getMarkdown() || '';
    const len = md.trim().length;
    $charCount.textContent = len;
    $tokenCount.textContent = estimateTokens(md);

    let hint = '', allowText = true;
    if (len === 0) { hint = '입력 없음'; allowText = false; }
    else if (len < 50) { hint = '50자 미만: 요청 불가'; allowText = false; }
    else if (len <= 150) { hint = '150자 이하: 결과 품질이 떨어질 수 있습니다(경고)'; }
    else if (len > MAX_LEN) { hint = '5000자 초과: 요약 요청 불가'; allowText = false; }
    $lengthHint.textContent = hint;

    const promptSelected = state.selectedPromptIdx !== null;
    const allowSave = len > 0 && len <= MAX_LEN;
    $temsetBtn.disabled = !promptSelected || (!allowText && state.mode !== 'file');
    $saveBtn.disabled   = !allowSave;
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

    card.appendChild(title);
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
    prompts.forEach((p, idx) => $buttonContainer.appendChild(makeCard(p, idx)));

    const wrapperWidth = document.querySelector('.nc-slide-container').clientWidth;
    state.cloneCount = Math.ceil(wrapperWidth / CARD_WIDTH);

    const originals = Array.from($buttonContainer.children);

    // 앞쪽 복제
    for (let i = prompts.length - state.cloneCount; i < prompts.length; i++) {
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

    // 초기 위치
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
    const p = prompts[index];

    // 상단 라벨
    $selectedName.textContent = `선택한 프롬프트: ${p.title}`;

    // 입력 캐시
    state.inputCache = state.editor.getMarkdown();

    // 👉 전환: 프롬프트 단계 숨김, 입력 단계 표시
    $promptStage.style.display = 'none';
    $inputStage.style.display  = 'flex';

    // 미리보기 체크 해제 + 슬라이드 재개
    document.querySelectorAll('.nc-peek-check').forEach(c => c.checked = false);
    state.peekChkIdx = null;
    resumeSlider();

    // 에디터 복원 + 상태
    if (state.inputCache) state.editor.setMarkdown(state.inputCache);
    updateCounters();
  }

  // ==== ⬅️ ‘프롬프트 선택으로 돌아가기’ → 선택 단계 복원 ====
  $btnBack.addEventListener('click', () => {
    state.inputCache = state.editor.getMarkdown();

    $inputStage.style.display  = 'none';
    $promptStage.style.display = 'block';

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
    setMsg('업로드 중...');
    const form = new FormData(); form.append('file', file);
    try {
      const res1 = await fetch('/api/files/upload', { method:'POST', body:form, headers:withCsrf({'Accept':'application/json'}), credentials:'same-origin' });
      const data1 = await safeJson(res1);
      if (!res1.ok || !data1.success) throw new Error(data1.message || `업로드 실패 (status=${res1.status})`);
      state.fileId = data1.gridfsId || data1.id; state.fileName = file.name;

      if (!$titleInput.value.trim()) {
        const baseName = file.name.replace(/\.[^/.]+$/, '');
        $titleInput.value = `${fmtDate()}_${baseName}`;
      }

      if (state.fileId) {
        try {
          const res2 = await fetch(`/api/files/preview-meta/${encodeURIComponent(state.fileId)}`, {
            method:'GET', headers:withCsrf({'Accept':'application/json'}), credentials:'same-origin'
          });
          const meta = await safeJson(res2);
          if (!res2.ok || !meta.success) throw new Error(meta.message || `미리보기 실패 (status=${res2.status})`);
          const { text, truncated, blocked, sizeBytes } = meta;
          state.truncated = !!truncated; state.blocked = !!blocked; state.sizeBytes = Number(sizeBytes) || 0;

          state.editor.setMarkdown(text || '');

          if (state.blocked) { state.mode='file'; setMsg(`파일이 너무 커서 요약을 진행할 수 없습니다. (크기: ${(state.sizeBytes/1024/1024).toFixed(1)}MB)`, 'error'); }
          else if (state.truncated) { state.mode='file'; setMsg('파일이 큽니다. 원본 전체를 대상으로 경제/차단 정책에 따라 요약합니다.', 'info'); }
          else { state.mode='text'; setMsg('파일 업로드 및 내용 추출 완료', 'success'); }
          updateCounters();
        } catch (e2) { console.error(e2); setMsg(`미리보기 실패: ${e2.message}`, 'error'); }
      }
    } catch (err) { console.error(err); setMsg(`업로드 실패: ${err.message}`, 'error'); }
  });

  // ==== LLM 요청 ====
  function setLoading(isLoading, message='요약 중...') {
    document.querySelectorAll('button').forEach(b => b.disabled = isLoading);
    const overlay = document.getElementById('nc-loadingOverlay');
    if (overlay) { overlay.style.display = isLoading ? 'flex' : 'none'; overlay.textContent = message; }
  }
  async function requestTextSummary(contentToSend, promptTitle) {
    const res = await fetch('/notion/create-text', {
      method:'POST', headers:withCsrf({'Content-Type':'application/json'}),
      body: JSON.stringify({ content: contentToSend, promptTitle })
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.error || '요약 실패');
    return data;
  }
  async function requestFileSummaryById(fileId, promptTitle) {
    const res = await fetch('/notion/create-by-id', {
      method:'POST', headers:withCsrf({'Content-Type':'application/json'}),
      body: JSON.stringify({ fileId, promptTitle })
    });
    const data = await res.json();
    return data;
  }

  // 요약하기
  $temsetBtn.addEventListener('click', async () => {
    if (state.selectedPromptIdx === null) { alert('프롬프트를 선택하세요.'); return; }
    const prompt = prompts[state.selectedPromptIdx];

    try {
      setLoading(true, '요약하는 중...');
      let response;
      if (state.mode === 'file') {
        if (!state.fileId) { alert('파일 정보가 없습니다.'); setLoading(false); return; }
        response = await requestFileSummaryById(state.fileId, prompt.title);
        if (response.success) {
          const warn = response.message ? `<div class="nc-warn">${response.message}</div>` : '';
		  state.editor = new toastui.Editor({
		      el: document.getElementById('nc-editor2'),
		      height: '500px',
		      initialEditType: 'markdown',
		      previewStyle: 'vertical',
		      usageStatistics: false
		    });
          $resultBox.innerHTML = `${warn}<div class="nc-editor2">${escapeHtml(response.summary || '')}</div>`;
          state.editor.setMarkdown(response.summary || '');
        } else {
          const msg = response.error || response.message || '요약 불가';
          $resultBox.innerHTML = `<div class="nc-error">${msg}</div>`;
        }
      } else {
        let contentToSend;
        if (!state.hasProcessedOnce) {
          const promptContent = prompt.content || '';
          const md = state.editor.getMarkdown().trim();
          if (!md) { alert('요약할 내용이 없습니다.'); setLoading(false); return; }
          contentToSend = `${md}\n\n프롬프트: ${promptContent}\n\n`;
        } else {
          contentToSend = state.editor.getMarkdown().trim();
          if (!contentToSend) { alert('요약할 내용이 없습니다.'); setLoading(false); return; }
        }
        response = await requestTextSummary(contentToSend, prompt.title);
        const warn = response.warn ? `<div class="nc-warn">${response.warn}</div>` : '';
        $resultBox.innerHTML = `${warn}<div class="nc-md">${escapeHtml(response.summary || '')}</div>`;
        state.editor.setMarkdown(response.summary || '');
        state.hasProcessedOnce = true;
        alert('요약이 완료되었습니다!');
      }
      updateCounters();
    } catch (err) {
      console.error(err);
      alert('요청 중 오류: ' + err.message);
    } finally {
      setLoading(false);
    }
  });

  // 저장하기
  $saveBtn.addEventListener('click', async () => {
    const title = $titleInput.value.trim();
    if (!title) { alert('제목을 입력하세요.'); return; }
    const summary = state.editor.getMarkdown();
    if (!summary.trim()) { alert('저장할 내용이 없습니다.'); return; }
    const promptId = prompts[state.selectedPromptIdx]?.promptId || 0;
    try {
      setLoading(true, '저장 중...');
      const res = await fetch('/notion/save-note', {
        method:'POST', headers:withCsrf({'Content-Type':'application/json'}),
        body: JSON.stringify({ title, summary, promptId })
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error || '저장 실패');

      sessionStorage.setItem('noteTitle', title);
      sessionStorage.setItem('noteContent', summary);
      sessionStorage.setItem('keywords', (data.keywords || []).join(', '));
      sessionStorage.setItem('categoryPath', data.categoryPath || '');
      sessionStorage.setItem('folderId', data.folderId || '');

      window.location.href = '/notion/complete';
    } catch (err) {
      console.error(err);
      alert('저장 중 오류: ' + err.message);
    } finally {
      setLoading(false);
    }
  });

  // 초기 렌더/카운터
  renderSlider();
  updateCounters();
});