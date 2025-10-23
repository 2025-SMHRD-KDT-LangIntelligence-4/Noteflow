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
		datesSet: () => setTimeout(() => injectPlusButtons(), 0),
		// 선택 모드일 때는 클릭이 '선택 토글', 평소엔 수정 모달 열기
		eventClick: (info) => {
			if (!info || !info.event || !info.event.id) return;
			if (_selectionModeOn) return; // 선택 모드에선 클릭 토글만(decorate 쪽에서 처리)
			openEditModal(info.event.id);
		},
		eventDidMount: (info) => {
			setTimeout(() => injectPlusButtons(), 0);
			// 🔴 체크박지/선택 뱃지 주입
			decorateEventForSelection(info);
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
// ================== 삭제 센터 ==================
let _selectionModeOn = false;
let _selectedEventIds = new Set();

// ① 선택삭제 모드 ON/OFF
function enableSelectionMode() {
	if (_selectionModeOn) return;
	_selectionModeOn = true;
	_selectedEventIds.clear();

	// ✅ 완전 재마운트: remove → addEventSource로 eventDidMount 재실행 보장
	if (calendar) {
		const current = _allEvents || [];
		calendar.batchRendering(() => {
			calendar.removeAllEvents();
			calendar.addEventSource(current);
		});
	}
	showSelectionBar();
	Swal.fire({
		icon: 'info',
		title: '선택 삭제 모드',
		html: '삭제할 일정을 클릭해서 선택하세요.<br>완료 후 <b>선택 n개 삭제</b> 버튼을 눌러주세요.',
		timer: 2000,
		showConfirmButton: false
	});
}

function disableSelectionMode() {
	_selectionModeOn = false;
	_selectedEventIds.clear();
	// 체크박스/선택 표시 제거
	document.querySelectorAll('.fc-event .sel-badge').forEach(n => n.remove());
	if (calendar) {
		const current = _allEvents || [];
		calendar.batchRendering(() => {
			calendar.removeAllEvents();
			calendar.addEventSource(current);
		});
	}
	hideSelectionBar();
}

// eventDidMount에서 체크박스/뱃지 주입
function decorateEventForSelection(info) {
	if (!_selectionModeOn) return;
	// 중복 주입 방지
	if (info.el.querySelector('.sel-badge')) return;
	info.el.style.position = 'relative';
	const wrap = document.createElement('label');
	wrap.className = 'sel-badge';
	wrap.title = _selectedEventIds.has(info.event.id) ? '선택됨' : '선택';
	const cb = document.createElement('input');
	cb.type = 'checkbox';
	cb.checked = _selectedEventIds.has(info.event.id);
	wrap.appendChild(cb);
	info.el.appendChild(wrap);

	const toggle = (e) => {
		if (!_selectionModeOn) return;
		e.preventDefault();
		e.stopPropagation();
		if (_selectedEventIds.has(info.event.id)) {
			_selectedEventIds.delete(info.event.id);
			cb.checked = false;
			wrap.title = '선택';
		} else {
			_selectedEventIds.add(info.event.id);
			cb.checked = true;
			wrap.title = '선택됨';

		}
		updateSelectedCountLabel();
	};
	wrap.addEventListener('click', toggle);
	info.el.addEventListener('click', (e) => { if (_selectionModeOn) toggle(e); }, true);
}

// ② 필터 일괄 삭제
async function openFilterDeleteDialog() {
  const { value: formValues } = await Swal.fire({
    title: '필터 조건으로 일괄 삭제',
    html: `
      <div style="display:flex;flex-direction:column;gap:8px;text-align:left">
        <label>기간</label>
        <input type="date" id="delStart" class="swal2-input" style="width:100%" placeholder="시작일">
        <input type="date" id="delEnd" class="swal2-input" style="width:100%" placeholder="종료일">
        <label>키워드(제목+내용)</label>
        <input type="text" id="delKeyword" class="swal2-input" placeholder="예: java, 회의">
        <label>카테고리(쉼표로 여러개)</label>
        <input type="text" id="delCats" class="swal2-input" placeholder="예: java,study">
      </div>
    `,
    focusConfirm: false,
    preConfirm: () => {
      return {
        start: (document.getElementById('delStart').value || '').trim(),
        end: (document.getElementById('delEnd').value || '').trim(),
        keyword: (document.getElementById('delKeyword').value || '').trim().toLowerCase(),
        cats: (document.getElementById('delCats').value || '')
          .split(',').map(v => v.trim().toLowerCase()).filter(Boolean),
      };
    },
    showCancelButton: true,
    confirmButtonText: '미리보기',
    cancelButtonText: '취소',
  });
  if (!formValues) return;

  // 미리보기(클라 계산)
  const ids = _allSchedulesRaw
    .filter(s => {
      // 기간
      const inRange = (() => {
        if (!formValues.start && !formValues.end) return true;
        const sdt = new Date(s.start_time);
        const sD  = formValues.start ? new Date(formValues.start + 'T00:00:00') : null;
        const eD  = formValues.end   ? new Date(formValues.end   + 'T23:59:59') : null;
        if (sD && sdt < sD) return false;
        if (eD && sdt > eD) return false;
        return true;
      })();
      if (!inRange) return false;

      // 키워드
      const kw = formValues.keyword;
      if (kw) {
        const title = (s.title || '').toLowerCase();
        const desc  = (s.description || '').toLowerCase();
        if (!title.includes(kw) && !desc.includes(kw)) return false;
      }

      // 카테고리
      if (formValues.cats.length) {
        const cats = (s.category || '').toLowerCase().split(',').map(v => v.trim());
        const hit = formValues.cats.some(c => cats.includes(c));
        if (!hit) return false;
      }
      return true;
    })
    .map(s => s.schedule_id);

  if (ids.length === 0) {
    Swal.fire({ icon: 'info', text: '삭제 대상이 없습니다.' });
    return;
  }

  const { isConfirmed } = await Swal.fire({
    icon: 'warning',
    title: `총 ${ids.length}개 일정 삭제`,
    html: `아래 입력창에 <b>삭제</b> 를 입력하면 진행됩니다.`,
    input: 'text',
    inputPlaceholder: '삭제',
    showCancelButton: true,
    confirmButtonText: '진짜 삭제',
    // ⚠️ 여기!
    inputValidator: (v) => (v === '삭제' ? undefined : '삭제 를 정확히 입력하세요'),
  });
  if (!isConfirmed) return;

  try {
    const resp = await fetchWithCsrf('/api/schedule/bulk-delete', {
      method: 'POST',
      body: JSON.stringify(ids.map(Number)),
    });
    await refreshEvents();
    Swal.fire({ icon: 'success', text: resp?.message || '삭제되었습니다.' });
  } catch (err) {
    console.error('필터 삭제 실패:', err);
    Swal.fire({ icon: 'error', text: `삭제 실패: ${err?.message || '알 수 없는 오류'}` });
  }
}

// ③ 최근 생성분 빠른 삭제
// ③ 최근 생성분 빠른 삭제 (미리보기 개수 포함)
async function openRecentDeleteDialog() {
  const { value: minutes } = await Swal.fire({
    title: '최근 생성 일정 빠른 삭제',
    input: 'range',
    inputAttributes: { min: 1, max: 10, step: 1 },
    inputValue: 5,
    inputLabel: '분',
    showCancelButton: true,
    confirmButtonText: '다음',
  });
  if (!minutes) return;

  // 1) 백엔드에서 미리보기 개수 가져오기
  let previewCount = 0;
  try {
    const preview = await fetchWithCsrf(`/api/schedule/bulk-delete-recent/preview?minutes=${Number(minutes)}`);
    previewCount = Number(preview?.count || 0);
  } catch (err) {
    console.error('미리보기 조회 실패:', err);
    Swal.fire({ icon: 'error', text: `미리보기 실패: ${err?.message || '알 수 없는 오류'}` });
    return;
  }

  // 2) 삭제 대상 없으면 안내 후 종료
  if (!previewCount) {
    Swal.fire({ icon: 'info', text: `최근 ${minutes}분 내 생성된 일정이 없습니다.` });
    return;
  }

  // 3) 컨펌 모달 (입력 검증은 inputValidator로 → isConfirmed만 확인)
  const { isConfirmed } = await Swal.fire({
    icon: 'warning',
    title: `최근 ${minutes}분 내 생성 일정 삭제`,
    html: `총 <b>${previewCount}</b>개가 삭제됩니다.<br>진행하려면 <b>삭제</b> 를 입력하세요.`,
    input: 'text',
    showCancelButton: true,
    confirmButtonText: '삭제',
    inputValidator: (v) => (v === '삭제' ? undefined : '삭제 를 정확히 입력하세요'),
  });
  if (!isConfirmed) return;

  // 4) 실제 삭제 호출
  try {
    const resp = await fetchWithCsrf(`/api/schedule/bulk-delete-recent?minutes=${Number(minutes)}`, {
      method: 'POST',
    });
    await refreshEvents();
    Swal.fire({ icon: 'success', text: resp?.message || '삭제되었습니다.' });
  } catch (err) {
    console.error('최근 삭제 실패:', err);
    Swal.fire({ icon: 'error', text: `삭제 실패: ${err?.message || '알 수 없는 오류'}` });
  }
}

// ④ 선택삭제 실제 실행
async function deleteSelectedNow() {
  if (!_selectedEventIds.size) {
    Swal.fire({ icon: 'info', text: '선택한 일정이 없습니다.' });
    return;
  }
  const count = _selectedEventIds.size;

  const { isConfirmed } = await Swal.fire({
    icon: 'warning',
    title: `선택 ${count}개 삭제`,
    html: `진행하려면 <b>삭제</b> 입력`,
    input: 'text',
    showCancelButton: true,
    // ⚠️ 여기! inputValidator로 검사만 하고, 통과 시 isConfirmed=true가 된다.
    inputValidator: (v) => (v === '삭제' ? undefined : '삭제 를 정확히 입력하세요'),
  });
  if (!isConfirmed) return;

  try {
    const ids = Array.from(_selectedEventIds).map(Number);
    const resp = await fetchWithCsrf('/api/schedule/bulk-delete', {
      method: 'POST',
      body: JSON.stringify(ids),
    });
    disableSelectionMode();
    await refreshEvents();
    Swal.fire({ icon: 'success', text: resp?.message || '삭제되었습니다.' });
  } catch (err) {
    console.error('선택 삭제 실패:', err);
    Swal.fire({ icon: 'error', text: `삭제 실패: ${err?.message || '알 수 없는 오류'}` });
  }
}

// 메인: 삭제 센터 모달
function openDeleteCenter() {
	Swal.fire({
		title: '삭제 옵션 선택',
		html: `
		<div style="display:grid;grid-template-columns:1fr;gap:10px;text-align:left">
		        <button id="optSel" class="btn danger" style="width:100%;">① 체크박스 기반 선택 삭제</button>
		        <button id="optFilter" class="btn danger" style="width:100%;">② 기간·키워드·카테고리로 일괄 삭제</button>
		        <button id="optRecent" class="btn danger" style="width:100%;">③ 최근 생성분 빠른 삭제</button>
		      </div>
    `,
		showConfirmButton: false,
		didOpen: () => {
			const $ = (sel) => Swal.getHtmlContainer().querySelector(sel);
			$('#optSel').onclick = () => {
				enableSelectionMode();
				Swal.close(); // 팝업은 닫고, 플로팅 패널로 제어
			};
			$('#optFilter').onclick = openFilterDeleteDialog;
			$('#optRecent').onclick = openRecentDeleteDialog;
			$('#selDeleteBtn').onclick = deleteSelectedNow;
			$('#selCancelBtn').onclick = () => {
				disableSelectionMode();
				const panel = $('#selActions');
				if (panel) panel.style.display = 'none';
			};
		}
	});
}

// 복구(휴지통)
async function openTrash() {
	// 서버에서 휴지통 목록 가져오기 (간단 버전)
	const trash = await fetchWithCsrf('/api/schedule/trash'); // [{id,title,start,end,updatedAt}, ...]
	if (!trash || !trash.length) { Swal.fire({ icon: 'info', text: '복구 가능한 항목이 없습니다.' }); return; }

	// 간단 선택 UI
	const html = ['<div style="text-align:left;max-height:300px;overflow:auto">'];
	trash.forEach(t => {
		html.push(`<label style="display:flex;gap:8px;align-items:center;margin:4px 0">
      <input type="checkbox" class="restoreBox" value="${t.schedule_id}">
      <span>${t.start_time?.slice(0, 16) || ''} ${t.title || '(제목 없음)'}</span>
    </label>`);
	});
	html.push('</div>');

	const { isConfirmed } = await Swal.fire({
		title: '복구할 항목 선택',
		html: html.join(''),
		showCancelButton: true,
		confirmButtonText: '선택 복구',
		didOpen: () => { }
	});
	if (!isConfirmed) return;

	const boxes = Swal.getHtmlContainer().querySelectorAll('.restoreBox:checked');
	const ids = Array.from(boxes).map(b => Number(b.value));
	if (!ids.length) return;

	await fetchWithCsrf('/api/schedule/bulk-restore', { method: 'POST', body: JSON.stringify(ids) });
	await refreshEvents();
	Swal.fire({ icon: 'success', text: '복구되었습니다.' });
}

// 버튼 와이어링
document.addEventListener('DOMContentLoaded', () => {
	const delBtn = document.getElementById('openDeleteCenterBtn');
	const trashBtn = document.getElementById('openTrashBtn');
	if (delBtn) delBtn.onclick = openDeleteCenter;
	if (trashBtn) trashBtn.onclick = openTrash;
});
// ----- 선택 모드 전용 플로팅 패널 -----
let _selBarEl = null;

function createSelectionBar() {
	if (_selBarEl) return _selBarEl;
	const bar = document.createElement('div');
	bar.id = 'selectionDeleteBar';
	bar.style.position = 'fixed';
	bar.style.right = '24px';
	bar.style.bottom = '24px';
	bar.style.zIndex = '2147483000';
	bar.style.background = 'rgba(33, 33, 33, 0.92)';
	bar.style.color = '#fff';
	bar.style.padding = '12px 14px';
	bar.style.borderRadius = '14px';
	bar.style.boxShadow = '0 6px 24px rgba(0,0,0,0.25)';
	bar.style.display = 'none';
	bar.style.gap = '10px';
	bar.style.alignItems = 'center';
	bar.style.minWidth = '280px';

	bar.innerHTML = `
    <span id="selCountLabel" style="font-weight:600">선택 0개</span>
    <span style="flex:1"></span>
    <button id="selBarDelete" class="btn danger" style="padding:6px 10px;border-radius:10px;">삭제</button>
    <button id="selBarCancel" class="btn secondary" style="padding:6px 10px;border-radius:10px;">취소</button>
  `;

	document.body.appendChild(bar);
	_selBarEl = bar;

	// 이벤트 바인딩
	bar.querySelector('#selBarDelete').onclick = deleteSelectedNow;
	bar.querySelector('#selBarCancel').onclick = disableSelectionMode;

	return bar;
}

function showSelectionBar() {
	const bar = createSelectionBar();
	bar.style.display = 'flex';
	updateSelectedCountLabel(); // 숫자 즉시 반영
}

function hideSelectionBar() {
	if (_selBarEl) _selBarEl.style.display = 'none';
}

function updateSelectedCountLabel() {
	const label = _selBarEl?.querySelector('#selCountLabel');
	if (label) label.textContent = `선택 ${_selectedEventIds.size}개`;
}
