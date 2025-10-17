import { formatDate, fetchWithCsrf, alertSuccess, alertError, formatTime } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';

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
