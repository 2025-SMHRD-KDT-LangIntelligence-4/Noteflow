document.addEventListener('DOMContentLoaded', () => {
  const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  const $fileInput     = document.getElementById('fileInput');
  const $fileUploadBtn = document.getElementById('file-upload');
  const $titleInput    = document.getElementById('inputTitle');
  const $contentInput  = document.getElementById('inputContent');
  const $templateBtn   = document.getElementById('template');
  const $msg           = document.getElementById('preMsg');

  // 상태
  const state = {
    fileId:    null,
    fileName:  null,
    truncated: false,
    blocked:   false,
    sizeBytes: 0,
    mode:      'text' // 'text' | 'file' (파일 전체 요약 모드)
  };

  // 메시지 표시
  const setMsg = (text, type = 'info') => {
    if (!$msg) return;
    $msg.textContent = text;
    $msg.style.color = type === 'error' ? 'red' : (type === 'success' ? 'green' : '#666');
  };

  // 템플릿 버튼 활성화 조건
  const enableTemplateIfReady = () => {
    const hasTitle   = $titleInput.value.trim().length > 0;
    const hasContent = $contentInput.value.trim().length > 0;
    // 차단이면 무조건 비활성화
    $templateBtn.disabled = state.blocked || !(hasTitle && hasContent);
  };

  // 날짜 포맷
  const fmtDate = () => {
    const d = new Date();
    return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
  };

  // CSRF 헤더 빌더
  const buildHeaders = (base = {}) => {
    const headers = { ...base };
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    return headers;
  };

  // JSON 가드
  const safeJson = async (res) => {
    const ct = res.headers.get('content-type') || '';
    if (!ct.includes('application/json')) {
      const body = await res.text();
      throw new Error(`JSON이 아닌 응답입니다 (status=${res.status}, ct=${ct})\n${body.slice(0,200)}`);
    }
    return res.json();
  };

  // preview-meta 호출 (미리보기 텍스트 + truncated + blocked)
  const fetchPreviewMeta = async (fileId) => {
    const res = await fetch(`/api/files/preview-meta/${encodeURIComponent(fileId)}`, {
      method: 'GET',
      headers: buildHeaders({ 'Accept': 'application/json' }),
      credentials: 'same-origin'
    });
    const data = await safeJson(res);
    if (!res.ok || !data.success) {
      throw new Error(data.message || `미리보기 메타 조회 실패 (status=${res.status})`);
    }
    return data; // { success, text, truncated, blocked, sizeBytes }
  };

  // 파일 업로드 버튼 → input 클릭
  $fileUploadBtn.addEventListener('click', () => $fileInput.click());

  // 파일 선택 시 업로드
  $fileInput.addEventListener('change', async () => {
    const file = $fileInput.files?.[0];
    if (!file) return;

    setMsg('업로드 중...');
    const form = new FormData();
    form.append('file', file);

    try {
      // 1) 업로드
      const res = await fetch('/api/files/upload', {
        method: 'POST',
        body: form,
        headers: buildHeaders({ 'Accept': 'application/json' }),
        credentials: 'same-origin'
      });
      const data = await safeJson(res);
      if (!res.ok || !data.success) throw new Error(data.message || `업로드 실패 (status=${res.status})`);

      state.fileId   = data.gridfsId || data.id;
      state.fileName = file.name;

      // 2) 제목 기본값
      if (!$titleInput.value.trim()) {
        const baseName = file.name.replace(/\.[^/.]+$/, '');
        $titleInput.value = `${fmtDate()}_${baseName}`;
      }

      // 3) preview-meta 조회
      if (state.fileId) {
        try {
          const meta = await fetchPreviewMeta(state.fileId);
          const { text, truncated, blocked, sizeBytes } = meta;

          state.truncated = !!truncated;
          state.blocked   = !!blocked;
          state.sizeBytes = Number(sizeBytes) || 0;

          $contentInput.value = text || '';

          // 상태/UX 결정
          if (state.blocked) {
            // 아주 큰 파일: 요약 진행 금지
            state.mode = 'file'; // 의미상 파일이지만, 다음 단계 자체가 막히므로 mode는 아무 의미 없음
            $contentInput.disabled = true;
            setMsg(`파일이 너무 커서 요약을 진행할 수 없습니다. (크기: ${(state.sizeBytes/1024/1024).toFixed(1)}MB)`, 'error');
          } else if (state.truncated) {
            // 큰 파일: 파일 전체 요약 모드로 전환, 수정 비활성화
            state.mode = 'file';
            $contentInput.disabled = true;
            setMsg('파일이 큽니다. 다음 단계에서 원본 전체를 대상으로 요약합니다.', 'info');
          } else {
            // 일반 파일: 텍스트 모드 (수정 가능)
            state.mode = 'text';
            $contentInput.disabled = false;
            setMsg('파일 업로드 및 내용 추출 완료', 'success');
          }

        } catch (e) {
          console.error(e);
          setMsg(`미리보기 실패: ${e.message}`, 'error');
        }
      }

      enableTemplateIfReady();
    } catch (err) {
      console.error(err);
      setMsg(`업로드 실패: ${err.message}`, 'error');
    }
  });

  // 입력 감시
  $titleInput.addEventListener('input', enableTemplateIfReady);
  $contentInput.addEventListener('input', enableTemplateIfReady);

  // 템플릿 버튼 → 다음 페이지로 이동
  $templateBtn.addEventListener('click', () => {
    if (state.blocked) {
      alert('파일이 너무 커서 요약을 진행할 수 없습니다. 파일을 분할하거나 텍스트를 직접 입력해 주세요.');
      return; // 차단: 진행 중지
    }

    const payload = {
      title:     $titleInput.value.trim(),
      content:   $contentInput.value.trim(), // mode==='file'이면 화면 텍스트는 참고용
      fileId:    state.fileId,
      fileName:  state.fileName,
      truncated: state.truncated,
      blocked:   state.blocked,
      sizeBytes: state.sizeBytes,
      mode:      state.mode  // 'text' | 'file'
    };
    sessionStorage.setItem('notion.precreate', JSON.stringify(payload));
    window.location.href = '/notion/create';
  });

  // 초기 상태
  enableTemplateIfReady();
});