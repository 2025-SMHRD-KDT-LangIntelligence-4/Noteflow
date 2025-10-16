// schedule-manager.js
// 일정 CRUD 및 FullCalendar 연동 메인 모듈

import { formatDate, fetchWithCsrf, alertSuccess, alertError } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';

document.addEventListener('DOMContentLoaded', async () => {
  initDropdowns();
  initColorDropdown();
  await loadCalendar();
});

let calendar; // FullCalendar 인스턴스 전역 관리

// --------------------- FullCalendar 초기화 ---------------------
const loadCalendar = async () => {
  const calendarEl = document.getElementById('calendar');

  calendar = new FullCalendar.Calendar(calendarEl, {
    locale: 'ko',
    initialView: 'dayGridMonth',
    selectable: true,
    editable: true,
    eventDisplay: 'block',
    eventColor: '#3788d8',

    // 이벤트 클릭 시 수정 모달 열기
    eventClick: async (info) => openEditModal(info.event),

    // 날짜 선택 시 등록 모달 열기
    select: (selectionInfo) => openCreateModal(selectionInfo),
  });

  calendar.render();
  await refreshEvents();
};

// --------------------- 일정 목록 새로고침 ---------------------
const refreshEvents = async () => {
  try {
    const events = await fetchWithCsrf('/schedule/list');
    calendar.removeAllEvents();
    calendar.addEventSource(events);
  } catch (err) {
    console.error('이벤트 불러오기 실패:', err);
    alertError('일정 불러오기에 실패했습니다.');
  }
};

// --------------------- 일정 등록 ---------------------
const openCreateModal = (selection) => {
  const startDateInput = document.querySelector('#startDate');
  const endDateInput = document.querySelector('#endDate');
  const startTimeInput = document.querySelector('#startTime');
  const endTimeInput = document.querySelector('#endTime');
  const colorPreview = document.querySelector('#colorPreview');

  startDateInput.value = formatDate(selection.start);
  endDateInput.value = formatDate(selection.end || selection.start);
  startTimeInput.value = '09:00';
  endTimeInput.value = '18:00';
  colorPreview.style.backgroundColor = '#3788d8';
  colorPreview.dataset.color = '#3788d8';

  document.querySelector('#createModal').classList.add('active');
};

// 등록 버튼 클릭
document.querySelector('#saveScheduleBtn')?.addEventListener('click', async () => {
  const data = collectFormData();
  try {
    await fetchWithCsrf('/schedule/add', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    alertSuccess('일정이 등록되었습니다.');
    closeModal();
    await refreshEvents();
  } catch {
    alertError('등록 중 오류가 발생했습니다.');
  }
});

// --------------------- 일정 수정 ---------------------
const openEditModal = (event) => {
  document.querySelector('#editTitle').value = event.title;
  document.querySelector('#editStartDate').value = formatDate(event.start);
  document.querySelector('#editEndDate').value = formatDate(event.end || event.start);
  document.querySelector('#editModal').dataset.eventId = event.id;
  document.querySelector('#editModal').classList.add('active');
};

// 수정 저장
document.querySelector('#updateScheduleBtn')?.addEventListener('click', async () => {
  const modal = document.querySelector('#editModal');
  const eventId = modal.dataset.eventId;
  const updatedData = collectFormData('edit');

  try {
    await fetchWithCsrf(`/schedule/update/${eventId}`, {
      method: 'PUT',
      body: JSON.stringify(updatedData),
    });
    alertSuccess('수정되었습니다.');
    closeModal();
    await refreshEvents();
  } catch {
    alertError('수정 중 오류가 발생했습니다.');
  }
});

// --------------------- 일정 삭제 ---------------------
document.querySelector('#deleteScheduleBtn')?.addEventListener('click', async () => {
  const modal = document.querySelector('#editModal');
  const eventId = modal.dataset.eventId;

  try {
    await fetchWithCsrf(`/schedule/delete/${eventId}`, { method: 'DELETE' });
    alertSuccess('삭제되었습니다.');
    closeModal();
    await refreshEvents();
  } catch {
    alertError('삭제 실패');
  }
});

// --------------------- 폼 데이터 수집 ---------------------
const collectFormData = (prefix = '') => {
  const get = (id) => document.querySelector(`#${prefix}${id}`)?.value || '';
  return {
    title: get('Title'),
    startDate: get('StartDate'),
    endDate: get('EndDate'),
    startTime: get('StartTime'),
    endTime: get('EndTime'),
    color: document.querySelector('#colorPreview')?.dataset.color || '#3788d8',
  };
};

// --------------------- 모달 닫기 ---------------------
const closeModal = () => {
  document.querySelectorAll('.modal.active').forEach((m) => m.classList.remove('active'));
};
