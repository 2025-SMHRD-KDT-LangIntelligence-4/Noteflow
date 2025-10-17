import { formatDate, fetchWithCsrf, alertSuccess, alertError, formatTime } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';

// schedule-quick-add.js에 정의된 함수를 사용하기 위해 전역 함수로 노출
// (schedule-quick-add.js가 type="module" 없이 로드된다면 window.injectPlusButtons로 노출)
// 현재는 두 파일 모두 type="module"이므로, import/export를 사용하거나,
// schedule-quick-add.js에서 injectPlusButtons를 전역 객체(window)에 할당해야 합니다.
// 여기서는 FullCalendar의 datesSet 콜백에서 전역 함수를 사용하도록 처리합니다.

document.addEventListener('DOMContentLoaded', async () => {
	initDropdowns();
	initColorDropdown();
	await loadCalendar();
	window.calendar = calendar;
	window.refreshEvents = refreshEvents;
});

let calendar;

const loadCalendar = async () => {
	const calendarEl = document.getElementById('calendar');
	calendar = new FullCalendar.Calendar(calendarEl, {
		locale: 'ko',
		initialView: 'dayGridMonth',
		selectable: true,
		editable: true,
		eventDisplay: 'block',
		
		// 1. FullCalendar 뷰 전환 설정 추가
		headerToolbar: {
			left: 'prev,next today',
			center: 'title',
			right: 'dayGridMonth,timeGridWeek' // '월, 주' 뷰 추가
		},
		views: {
			timeGridWeek: { 
				type: 'timeGrid',
				duration: { weeks: 1 },
				buttonText: '주간'
			},
			dayGridMonth: { 
				buttonText: '월간'
			}
		},
		
		// 2. 뷰 변경/날짜 이동 시 + 버튼 재주입
		datesSet: (info) => { 
			// window에 injectPlusButtons가 노출되었다고 가정하고 호출
			if (window.injectPlusButtons && typeof window.injectPlusButtons === 'function') {
				window.injectPlusButtons();
			}
		},

		eventClick: async (info) => openEditModal(info.event),
		select: (selectionInfo) => openCreateModal(selectionInfo),
		events: {
			url: '/api/schedule/period',
			method: 'GET',
			failure: () => alertError('일정 데이터를 불러오지 못했습니다.'),
		},
	});
	calendar.render();
	await refreshEvents();
	
	// 초기 로드 시 버튼 주입 (schedule-quick-add.js의 DOMContentLoaded와 중복될 수 있음)
	if (window.injectPlusButtons && typeof window.injectPlusButtons === 'function') {
		window.injectPlusButtons();
	}
};

const refreshEvents = async () => {
	try {
		const schedules = await fetchWithCsrf('/api/schedule');
		const events = schedules.map(s => ({
			id: s.schedule_id,
			title: s.title,
			start: s.start_time,
			end: s.end_time,
			color: s.color_tag || '#3788d8',
			allDay: !!s.is_all_day,
			extendedProps: {
				description: s.description || '',
				emoji: s.emoji || null,
				isAllDay: !!s.is_all_day,
			}
		}));
		calendar.removeAllEvents();
		calendar.addEventSource(events);
	} catch (err) {
		console.error(err);
		alertError('일정 불러오기에 실패했습니다.');
	}
};

const collectFormData = (prefix = '') => {
	const get = (id) => document.querySelector(`#${prefix}${id}`)?.value || document.querySelector(`#${id}`)?.value || '';
	const colorEl = document.querySelector(`#${prefix}ColorPreview`) || document.querySelector('#colorPreview');
	const color = colorEl?.dataset?.color || '#3788d8';
	const allDayEl = document.querySelector(`#${prefix}AllDay`) || document.querySelector('#allDay');
	return {
		title: get('Title'),
		description: get('Description'),
		startDate: get('StartDate'),
		endDate: get('EndDate'),
		startTime: get('StartTime'),
		endTime: get('EndTime'),
		isAllDay: !!(allDayEl && allDayEl.checked),
		colorTag: color,
	};
};

const closeModal = () => {
	document.querySelectorAll('.modal.active').forEach((m) => m.classList.remove('active'));
};
