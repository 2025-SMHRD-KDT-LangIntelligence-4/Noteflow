// schedule-quick-add.js
// FullCalendar 셀에 + 버튼을 삽입하고 Quick Add 모달을 여는 보조 스크립트

document.addEventListener('DOMContentLoaded', () => {

  // ------------------------------ + 버튼 주입 ------------------------------
  function injectPlusButtons() {
    document.querySelectorAll('.fc-daygrid-day').forEach(dayCell => {
      if (dayCell.querySelector('.day-plus-btn')) return; // 중복 방지
      const btn = document.createElement('button');
      btn.className = 'day-plus-btn';
      btn.type = 'button';
      btn.title = '일정 추가';
      btn.innerText = '+';

      // 날짜 정보 가져오기 (FullCalendar data-date 속성)
      const dateStr = dayCell.getAttribute('data-date') ||
        dayCell.querySelector('.fc-daygrid-day-top')?.getAttribute('data-date') || '';
      if (dateStr) btn.dataset.date = dateStr;

      // 클릭 시 Quick Add 모달 열기
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        openQuickAddModal(btn.dataset.date, btn);
      });

      // 우상단 배치
      dayCell.style.position = 'relative';
      dayCell.appendChild(btn);
    });
  }

  // ------------------------------ 모달 관련 ------------------------------
  const quickModal = document.getElementById('quickAddModal');
  const qaTitle = document.getElementById('qaTitle');
  const qaDesc = document.getElementById('qaDesc');
  const qaStartDate = document.getElementById('qaStartDate');
  const qaEndDate = document.getElementById('qaEndDate');
  const qaStartTime = document.getElementById('qaStartTime');
  const qaEndTime = document.getElementById('qaEndTime');
  const qaAllDay = document.getElementById('qaAllDay');
  const qaColor = document.getElementById('qaColor');
  const qaNotify = document.getElementById('qaNotify');
  const qaSave = document.getElementById('qaSave');
  const qaCancel = document.getElementById('qaCancel');

  function openQuickAddModal(dateStr) {
    qaTitle.value = '';
    qaDesc.value = '';
    qaColor.value = '#3788d8';
    qaAllDay.checked = false;
    qaNotify.value = '0';

    const today = dateStr ? new Date(dateStr) : new Date();
    const dateISO = today.toISOString().slice(0, 10);

    qaStartDate.value = dateStr || dateISO;
    qaEndDate.value = dateStr || dateISO;
    qaStartTime.value = '09:00';
    qaEndTime.value = '10:00';

    quickModal.classList.remove('hidden');
    quickModal.setAttribute('aria-hidden', 'false');
  }

  function closeQuickAddModal() {
    quickModal.classList.add('hidden');
    quickModal.setAttribute('aria-hidden', 'true');
  }

  // ------------------------------ 저장 로직 ------------------------------
  qaSave.addEventListener('click', async () => {
    const payload = {
      title: qaTitle.value.trim() || '(제목 없음)',
      description: qaDesc.value.trim(),
      colorTag: qaColor.value,
      isAllDay: qaAllDay.checked,
      startTime: qaStartDate.value + 'T' + (qaAllDay.checked ? '00:00:00' : qaStartTime.value + ':00'),
      endTime: qaEndDate.value + 'T' + (qaAllDay.checked ? '23:59:59' : qaEndTime.value + ':00'),
      notifyMinutesBefore: qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10)
    };

    try {
      const csrfToken = document.querySelector('meta[name="_csrf"]').content;
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

      const res = await fetch('/api/schedule/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) throw new Error('생성 실패');

      // 수정: 전역 refreshEvents 함수 우선 호출, 없으면 calendar.refetchEvents, 없으면 경고
      // 기존: if (window.calendar && typeof window.calendar.refetchEvents === 'function') { ... }
      // → 수정: 우선 window.refreshEvents 호출 // [수정]
      if (window.refreshEvents && typeof window.refreshEvents === 'function') {
        await window.refreshEvents();
      } else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
        await window.calendar.refetchEvents();
      } else {
        console.warn('global calendar instance not found. manual refresh required.');
      }

      closeQuickAddModal();
    } catch (err) {
      console.error(err);
      alert('일정 생성 중 오류가 발생했습니다.');
    }
  });

  qaCancel.addEventListener('click', closeQuickAddModal);

  quickModal.addEventListener('click', (e) => {
    if (e.target === quickModal) closeQuickAddModal();
  });

  // ------------------------------ 기타 UI ------------------------------
  const searchBtn = document.getElementById('calendarSearchBtn');
  if (searchBtn) {
    searchBtn.addEventListener('click', () => {
      alert('검색 기능은 추후 구현 예정입니다.');
    });
  }

  // ------------------------------ FullCalendar 렌더 감시 ------------------------------
  const interval = setInterval(() => {
    const dayCells = document.querySelectorAll('.fc-daygrid-day');
    if (dayCells.length > 0) {
      injectPlusButtons();
      clearInterval(interval);

      const calRoot = document.getElementById('calendar');
      if (calRoot) {
        const mo = new MutationObserver(() => injectPlusButtons());
        mo.observe(calRoot, { childList: true, subtree: true });
      }
    }
  }, 300);
});
