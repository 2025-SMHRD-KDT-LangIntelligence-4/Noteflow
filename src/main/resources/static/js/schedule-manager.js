import { fetchWithCsrf, alertError } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';
// [개선]: schedule-quick-add.js에서 export한 함수들을 import하여 사용
import { injectPlusButtons, openQuickAddModal, closeQuickAddModal } from './schedule-quick-add.js';
import { openEditModal } from './schedule-edit.js';
let calendar;
let _allSchedulesRaw = [];  // 서버 원본(일정 배열)
let _allEvents = [];        // fullcalendar 이벤트 배열 (현재 렌더 기준)

// ------------------ 카테고리 필터 ------------------
function filterEventsByCategory(categoryFilter) {
	const f = (categoryFilter || '').toLowerCase();
	const all = calendar.getEvents();
	if (!f || f === 'all') {
		all.forEach(e => e.setProp('display', 'block'));
		return;
	}
	all.forEach(e => {
		const props = e.extendedProps || e._def.extendedProps || {};
		const cats = (props.category || '')
			.split(',')
			.map(v => v.trim())
			.filter(Boolean);
		e.setProp('display', cats.includes(f) ? 'block' : 'none');
	});
}
// ------------------ 검색(제목+내용) ------------------
function applyClientSearch(keyword) {
	const q = (keyword || '').trim().toLowerCase();
	if (!q) {
		// 비우면 전체 보이기
		calendar.getEvents().forEach(ev => ev.setProp('display', 'block'));
		return;
	}
	calendar.getEvents().forEach(ev => {
		const p = ev.extendedProps || ev._def.extendedProps || {};
		const title = (ev.title || '').toLowerCase();
		const desc = (p.description || '').toLowerCase();
		const hit = title.includes(q) || desc.includes(q);
		ev.setProp('display', hit ? 'block' : 'none');
	});
}
// ------------------ 캘린더 초기화 ------------------
function initCalendar() {
	const el = document.getElementById('calendar');
	if (!el) return;

	calendar = new FullCalendar.Calendar(el, {
		locale: 'ko',
		initialView: 'dayGridMonth',
		height: 'auto',
		eventDisplay: 'block',        // ← 달력 전체에서 칩 스타일로 표시
		eventTextColor: '#ffffff',    // ← 기본 글자색을 흰색으로
		headerToolbar: {
			left: 'prev,next today',
			center: 'title',
			right: 'dayGridMonth,timeGridWeek,timeGridDay'
		},
		datesSet: async () => {
			// 월 이동 시에도 플러스 버튼 재주입
			setTimeout(() => injectPlusButtons(), 0);
		},
		eventClick: (info) => {
			// 일정 클릭 -> 수정 모달
			if (!info || !info.event || !info.event.id) return;
			openEditModal(info.event.id);
		},
		eventDidMount: () => {
			// 날짜 셀 그려진 후 플러스 버튼 다시 주입
			setTimeout(() => injectPlusButtons(), 0);
		}
	});

	calendar.render();
	window.calendar = calendar; // quick-add의 fallback(refetchEvents) 대비
}

// ------------------ 자동 카테고리 + 직접 입력 ------------------
function renderAutoCategories() {
	const listEl = document.getElementById('categoryFilterList');
	if (!listEl) return;

	// 기본 틀 유지(+직접 입력 포함)
	listEl.innerHTML = `
    <span class="category-tag active" data-filter="all">#전체</span>
    <span class="category-tag input-placeholder" id="catCustomAddBtn">+ 직접 입력</span>
    <span class="category-inline-input" id="catCustomInline" style="display:none;">
      <input type="text" id="catCustomInput" placeholder="카테고리 입력" style="padding:4px 8px;border:1px solid #ccc;border-radius:8px;font-size:12px;width:120px;" />
      <button id="catCustomOk" class="btn small">추가</button>
      <button id="catCustomCancel" class="btn small">취소</button>
    </span>
  `;

	// 집계
	const counts = {};
	_allSchedulesRaw.forEach(s => {
		(s.category || '')
			.toLowerCase()
			.split(',')
			.map(v => v.trim())
			.filter(Boolean)
			.forEach(c => counts[c] = (counts[c] || 0) + 1);
	});

	Object.entries(counts)
		.filter(([, cnt]) => cnt >= 5)
		.sort((a, b) => b[1] - a[1])
		.slice(0, 12)
		.forEach(([key, cnt]) => {
			const span = document.createElement('span');
			span.className = 'category-tag';
			span.dataset.filter = key;
			span.textContent = `#${key} (${cnt})`;
			listEl.appendChild(span);
		});

	// 위임 이벤트: 클릭/추가/취소
	// (중복 바인딩 방지: 기존 리스너 제거 후 한 번만 등록)
	const cloned = listEl.cloneNode(true);
	listEl.parentNode.replaceChild(cloned, listEl);

	cloned.addEventListener('click', (e) => {
		const addBtn = e.target.closest('#catCustomAddBtn');
		const okBtn = e.target.closest('#catCustomOk');
		const cancelBtn = e.target.closest('#catCustomCancel');
		const tag = e.target.closest('.category-tag');

		const inline = cloned.querySelector('#catCustomInline');
		const input = cloned.querySelector('#catCustomInput');

		if (addBtn) {
			inline.style.display = 'inline-flex';
			input.value = '';
			input.focus();
			return;
		}
		if (okBtn) {
			const v = (input.value || '').trim().toLowerCase();
			if (!v) return;
			// 동적 칩 추가
			const span = document.createElement('span');
			span.className = 'category-tag';
			span.dataset.filter = v;
			span.textContent = `#${v}`;
			cloned.appendChild(span);

			// 선택 상태 전환 + 필터 적용
			cloned.querySelectorAll('.category-tag').forEach(t => t.classList.remove('active'));
			span.classList.add('active');
			filterEventsByCategory(v);

			inline.style.display = 'none';
			return;
		}
		if (cancelBtn) {
			inline.style.display = 'none';
			return;
		}
		if (tag && tag.dataset.filter) {
			// 일반 카테고리 칩 클릭
			cloned.querySelectorAll('.category-tag').forEach(t => t.classList.remove('active'));
			tag.classList.add('active');
			filterEventsByCategory(tag.dataset.filter);
			return;
		}
	});
}


// ------------------ 검색 박스 바인딩 ------------------
function wireSearchBox() {
	const searchInput = document.getElementById('scheduleSearchInput');
	const searchBtn = document.getElementById('searchButton');
	const resetBtn = document.getElementById('searchResetButton');

	if (!searchInput || !searchBtn) return;

	const doSearch = () => {
		const keyword = searchInput.value || '';
		applyClientSearch(keyword);  // 제목+내용 클라이언트 필터
	};

	searchBtn.onclick = doSearch;
	searchInput.addEventListener('keydown', (e) => {
		if (e.key === 'Enter') doSearch();
	});

	if (resetBtn) {
		resetBtn.onclick = async () => {
			searchInput.value = '';
			await refreshEvents(); // 전체 초기화
		};
	}
}

// ------------------ 초기화 ------------------
document.addEventListener('DOMContentLoaded', async () => {
  initCalendar();
  await refreshEvents();
  wireSearchBox();
  // 플러스 버튼 주입(최초)
  setTimeout(() => injectPlusButtons(), 0);
});


const tempContainer = document.getElementById('tempScheduleContainer');
// [추가]: 임시 저장 목록을 UI에 표시
// [수정]: 임시 저장 목록을 UI에 표시
/*
export const loadTempSchedules = async () => {
	if (!tempContainer) return;

	try {
		const temps = await fetchWithCsrf('/api/schedule/temp-list');
	    
		tempContainer.innerHTML = ''; // 기존 내용 초기화
	    
		if (temps && temps.length > 0) {
			temps.slice(0, 5).forEach(temp => { // 최대 5개 표시
				const tag = document.createElement('div');
				tag.className = 'temp-tag';
				tag.dataset.id = temp.temp_id; 
				tag.title = temp.title;
			    
				tag.innerHTML = `
					<span class="temp-tag-title">${temp.title || '(제목 없음)'}</span>
					<span class="temp-tag-close" data-action="delete">X</span>
				`;
			    
				tempContainer.appendChild(tag);
    
				tag.addEventListener('click', (e) => {
					if (e.target.dataset.action === 'delete') {
						e.stopPropagation(); // 삭제 버튼 클릭 시 이벤트 전파 방지
						deleteTempSchedule(temp.temp_id);
					} else {
						// TODO: 임시 일정 내용을 모달에 로드하는 로직 구현 (openEditModal 사용 예정)
						alertSuccess(`ID ${temp.temp_id}의 임시 일정 불러오기 (로직 추가 필요)`);
					}
				});
			});
		} else {
			// 임시 일정이 없을 경우 메시지 표시
			 tempContainer.innerHTML = `<div class="no-temp-schedules">저장된 임시 일정이 없습니다.</div>`;
		}

	} catch (err) {
		console.error('임시 일정 불러오기 실패:', err);
		 tempContainer.innerHTML = `<div class="no-temp-schedules">목록 로드 오류.</div>`;
	}
};


// [추가]: 임시 일정 삭제 API 호출 (UI에서도 사용 가능)
const deleteTempSchedule = async (tempId) => {
	try {
		// [가정]: 임시 저장 삭제 API 엔드포인트는 '/api/schedule/temp-delete/{id}' 입니다.
		await fetchWithCsrf(`/api/schedule/temp-delete/${tempId}`, { method: 'DELETE' });
		alertSuccess('임시 일정이 삭제되었습니다.');
		loadTempSchedules(); // 목록 새로고침
	} catch (err) {
		console.error('임시 일정 삭제 실패:', err);
		alertError('임시 일정 삭제에 실패했습니다.');
	}
};
*/

// ------------------ 데이터 로드 & 렌더 ------------------
export const refreshEvents = async () => {
	try {
		const schedules = await fetchWithCsrf('/api/schedule');
		_allSchedulesRaw = schedules || [];
		if (!schedules) return;

		_allEvents = schedules.map(s => ({
			id: s.schedule_id,
			title: s.title,
			start: s.start_time,
			end: s.end_time,
			color: s.color_tag || '#3788d8',
			allDay: !!s.is_all_day,
			display: 'block',     // ← 혹시 전역 옵션이 못 먹었을 때도 칩 스타일 강제
			textColor: '#ffffff', // ← 개별 이벤트 글자색도 흰색으로
			extendedProps: {
				description: s.description || '',
				emoji: s.emoji || null,
				isAllDay: !!s.is_all_day,
				category: (s.category || '').toLowerCase(), // 소문자 정규화
				highlightType: (s.highlight_type || '').toLowerCase()
			}
		}));

		calendar.removeAllEvents();
		calendar.addEventSource(_allEvents);

		// 사이드바 자동 카테고리 재생성
		renderAutoCategories();
	} catch (err) {
		console.error(err);
		alertError('일정 불러오기에 실패했습니다.');
	}
};
window.refreshEvents = refreshEvents;

