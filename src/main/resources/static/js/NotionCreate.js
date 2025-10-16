document.addEventListener('DOMContentLoaded', () => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

  const $fileInput = document.getElementById('fileInput');
  const $fileUploadBtn = document.getElementById('file-upload');
  const $titleInput = document.getElementById('inputTitle');
  const $contentInput = document.getElementById('inputContent');
  const $templateBtn = document.getElementById('template');
  const $msg = document.getElementById('preMsg');

  const state = { fileId: null, fileName: null };

  const setMsg = (text, type='info') => {
    if ($msg) {
      $msg.textContent = text;
      $msg.style.color = type === 'error' ? 'red' : (type === 'success' ? 'green' : '#666');
    }
  };

  const enableTemplateIfReady = () => {
    const hasTitle = $titleInput.value.trim().length > 0;
    const hasContent = $contentInput.value.trim().length > 0;
    $templateBtn.disabled = !(hasTitle && hasContent);
  };

  const fmtDate = () => {
    const d = new Date();
    return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
  };

  // 파일 업로드 버튼 → 실제 input 클릭
  $fileUploadBtn.addEventListener('click', () => $fileInput.click());

  // 파일 선택 시 업로드
  $fileInput.addEventListener('change', async () => {
    const file = $fileInput.files?.[0];
    if (!file) return;

    setMsg('업로드 중...');
    const form = new FormData();
    form.append('file', file);

    try {
      const res = await fetch('/api/files/upload', {
        method: 'POST',
        body: form,
        headers: csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : undefined
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.message || '업로드 실패');

      state.fileId = data.gridfsId || data.id;
      state.fileName = file.name;

      // 제목 기본값
      if (!$titleInput.value.trim()) {
        const baseName = file.name.replace(/\.[^/.]+$/, '');
        $titleInput.value = `${fmtDate()}_${baseName}`;
      }

      // 미리보기 텍스트
      if (state.fileId) {
        const prev = await fetch(`/api/files/preview/${encodeURIComponent(state.fileId)}`);
        if (prev.ok) {
          const text = await prev.text();
          $contentInput.value = text;
          setMsg('파일 업로드 및 내용 추출 완료', 'success');
        } else {
          setMsg('미리보기 실패: 지원하지 않는 형식', 'error');
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
    const payload = {
      title: $titleInput.value.trim(),
      content: $contentInput.value.trim(),
      fileId: state.fileId,
      fileName: state.fileName
    };
    sessionStorage.setItem('notion.precreate', JSON.stringify(payload));
    window.location.href = '/notion/create';
  });

  enableTemplateIfReady();
});