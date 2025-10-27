import { fetchWithCsrf, alertError, alertSuccess } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';
// [개선]: schedule-quick-add.js에서 export한 함수들을 import하여 사용
import { injectPlusButtons, openQuickAddModal, closeQuickAddModal } from './schedule-quick-add.js';
import { openEditModal } from './schedule-edit.js';
let calendar;
let _allSchedulesRaw = [];  // 서버 원본(일정 배열)
let _allEvents = [];        // fullcalendar 이벤트 배열 (현재 렌더 기준)
let _hlByDate = {}; // {'yyyy-MM-dd': {symbol,color,note}}

// ── 하이라이트 SVG 렌더러 (모든 도형을 inline SVG로 통일) ─────────────────
function renderHighlightSVG(containerEl, symbol) {
	// 기존 내용 제거
	containerEl.innerHTML = '';

	const SVG_NS = 'http://www.w3.org/2000/svg';
	const svg = document.createElementNS(SVG_NS, 'svg');
	svg.setAttribute('viewBox', '0 0 100 100');
	svg.setAttribute('aria-hidden', 'true');

	let shape;
	if (symbol === 'circle') {
		shape = document.createElementNS(SVG_NS, 'circle');
		shape.setAttribute('cx', '50');
		shape.setAttribute('cy', '50');
		shape.setAttribute('r', '36'); // 숫자 가림 최소화
	} else if (symbol === 'square') {
		shape = document.createElementNS(SVG_NS, 'rect');
		shape.setAttribute('x', '14');
		shape.setAttribute('y', '14');
		shape.setAttribute('width', '72');
		shape.setAttribute('height', '72');
		shape.setAttribute('rx', '12'); // 살짝 둥글게
	} else if (symbol === 'triangle') {
		shape = document.createElementNS(SVG_NS, 'polygon');
		shape.setAttribute('points', '50,10 90,88 10,88');
	} else if (symbol === 'star') {
		shape = document.createElementNS(SVG_NS, 'path');
		// 가독성과 숫자 가림 최소화를 고려한 별 경로
		shape.setAttribute('d', 'M50 8 L61 38 L92 38 L66 56 L76 88 L50 70 L24 88 L34 56 L8 38 L39 38 Z');
	} else {
		// 알 수 없는 심볼은 렌더 생략
		return;
	}

	// 공통 스타일 플래그 (CSS에서 잡아줌)
	shape.setAttribute('data-outline', '1');

	svg.appendChild(shape);
	containerEl.appendChild(svg);
}










// ------------------ 임시 저장 사이드바 ------------------
const tempContainer = document.getElementById('tempScheduleContainer');

// 임시 일정 목록 로드 & 칩 렌더
async function loadTempDrafts() {
	if (!tempContainer) return;
	try {
		const temps = await fetchWithCsrf('/api/temp-schedule/list');
		tempContainer.innerHTML = '';
		if (!temps || temps.length === 0) {
			tempContainer.innerHTML = `<div class="no-temp-schedules">저장된 임시 일정이 없습니다.</div>`;
			return;
		}
		// 최대 10개 표시(원하면 조절)
		temps.slice(0, 10).forEach(t => {
			const tag = document.createElement('div');
			tag.className = 'temp-tag';
			tag.dataset.id = t.temp_id;
			tag.title = t.title || '(제목 없음)';
			tag.style.display = 'flex';
			tag.style.alignItems = 'center';
			tag.style.gap = '8px';
			tag.style.padding = '6px 10px';
			tag.style.borderRadius = '14px';
			tag.style.background = '#276EF1'; // 파란 배경
			tag.style.color = '#fff';         // 흰 글자
			tag.style.border = 'none';
			tag.style.cursor = 'pointer';
			tag.style.fontWeight = '600';
			tag.style.fontSize = '12px';
			tag.style.boxShadow = '0 2px 6px rgba(0,0,0,0.12)';
			// hover 효과(선택)
			tag.addEventListener('mouseenter', () => { tag.style.filter = 'brightness(0.95)'; });
			tag.addEventListener('mouseleave', () => { tag.style.filter = 'none'; });
			tag.innerHTML = `
			  <span class="temp-tag-title" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:150px;">
			    ${t.title || '(제목 없음)'}
			  </span>
			  <span class="temp-tag-close" title="삭제" style="
			    margin-left:auto;
			    cursor:pointer;
			    font-weight:700;
			    opacity:.9;
			  ">✕</span>
			`;
			tempContainer.appendChild(tag);

			tag.addEventListener('click', async (e) => {
				// X 눌렀을 때는 삭제
				if (e.target && e.target.classList.contains('temp-tag-close')) {
					e.stopPropagation();
					try {
						await fetchWithCsrf(`/api/temp-schedule/${t.temp_id}`, { method: 'DELETE' });
						alertSuccess('임시 일정이 삭제되었습니다.');
						loadTempDrafts();
						if (window.refreshTempBadges) await window.refreshTempBadges();
					} catch (err) {
						console.error(err);
						alertError('임시 일정 삭제 실패');
					}
					return;
				}
				// 칩 클릭 -> 드래프트를 불러와 빠른 등록 모달에 채우기
				try {
					const draft = await fetchWithCsrf(`/api/temp-schedule/${t.temp_id}`);
					// 모달 열고 내용 채우기 (quick-add.js에 헬퍼를 노출시키는 게 깔끔하지만
					// import 순환 이슈가 있으면 아래처럼 이벤트로 통신해도 됨)
					window.dispatchEvent(new CustomEvent('OPEN_QUICK_ADD_WITH_DRAFT', { detail: draft }));
				} catch (err) {
					console.error(err);
					alertError('임시 일정 불러오기 실패');
				}
			});
		});
	} catch (err) {
		console.error('임시 목록 로드 실패', err);
		tempContainer.innerHTML = `<div class="no-temp-schedules">목록 로드 오류.</div>`;
	}
}
window.loadTempDrafts = loadTempDrafts;

// quick-add 쪽에서 이벤트를 받아 모달 채우게끔 브릿지
window.addEventListener('OPEN_QUICK_ADD_WITH_DRAFT', (e) => {
	// quick-add가 export한 openQuickAddModal/ fill... 를 활용하고 싶다면,
	// 거기서 window에 핸들러를 등록해두는 방식이 충돌이 적음.
	if (window.openQuickAddFromDraft) {
		window.openQuickAddFromDraft(e.detail);
	} else {
		// fallback: 그냥 모달 열기 이벤트만 날려도 되고…
	}
});

// ------------------ 달력 + 버튼 옆 임시 뱃지 ------------------
let _tempDateCounts = {}; // {'yyyy-MM-dd': 2, ...}

async function refreshTempBadges() {
	if (!calendar) return;
	const view = calendar.view;
	// view.activeStart ~ view.activeEnd 기준
	const start = new Date(view.activeStart);
	const end = new Date(view.activeEnd);
	// ISO 로 변환
	const toIso = (d) => new Date(+d - d.getTimezoneOffset() * 60000).toISOString(); // tz 보정
	const counts = await fetchWithCsrf(`/api/temp-schedule/date-counts?start=${toIso(start)}&end=${toIso(end)}`);
	_tempDateCounts = counts || {};
	drawTempBadgesOnDays();
}
window.refreshTempBadges = refreshTempBadges;

// 하이라이트 
async function refreshDayHighlights() {
	if (!calendar) return;
	const view = calendar.view;
	const start = new Date(view.activeStart);
	const end = new Date(view.activeEnd);
	const toDate = (d) => new Date(+d - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
	const data = await fetchWithCsrf(`/api/day-highlights?start=${toDate(start)}&end=${toDate(end)}`);
	_hlByDate = (data && data.items) || {};
	drawHighlightsOnDays();
}

function drawTempBadgesOnDays() {
	document.querySelectorAll('.fc-daygrid-day').forEach(dayCell => {
		const dateStr = dayCell.getAttribute('data-date'); // yyyy-MM-dd
		if (!dateStr) return;
		// 기존 배지 제거
		const old = dayCell.querySelector('.day-draft-badge');
		if (old) old.remove();
		const cnt = _tempDateCounts[dateStr];
		if (cnt > 0) {
			const top = dayCell.querySelector('.fc-daygrid-day-top');
			if (!top) return;
			const btn = document.createElement('button');
			btn.className = 'day-draft-badge';
			btn.textContent = `📝${cnt}`;
			btn.title = '임시 저장된 일정 불러오기';
			btn.style.marginLeft = '6px';
			btn.style.fontSize = '11px';
			btn.style.padding = '0 6px';
			btn.style.borderRadius = '10px';
			btn.style.border = '1px solid #ddd';
			btn.style.background = '#fff';
			btn.style.cursor = 'pointer';
			btn.addEventListener('click', async (e) => {
				e.stopPropagation();
				try {
					// 해당 날짜의 임시들만 간단 선택 UI로 노출
					const list = await fetchWithCsrf('/api/temp-schedule/list');
					const only = (list || []).filter(x => (x.start_time || '').startsWith(dateStr));
					if (!only.length) { alertError('해당 날짜에 임시 일정이 없습니다.'); return; }
					const html = ['<div style="text-align:left;max-height:260px;overflow:auto">'];
					only.forEach(t => {
						html.push(`
              <label style="display:flex;gap:8px;align-items:center;margin:4px 0">
                <input type="radio" name="pickDraft" value="${t.temp_id}">
                <span>${(t.start_time || '').slice(11, 16)} ${t.title || '(제목 없음)'}</span>
              </label>
            `);
					});
					html.push('</div>');
					const { isConfirmed } = await Swal.fire({
						title: '임시 일정 선택',
						html: html.join(''),
						showCancelButton: true,
						confirmButtonText: '불러오기'
					});
					if (!isConfirmed) return;
					const picked = Swal.getHtmlContainer().querySelector('input[name="pickDraft"]:checked');
					if (!picked) return;
					const draft = await fetchWithCsrf(`/api/temp-schedule/${picked.value}`);
					window.dispatchEvent(new CustomEvent('OPEN_QUICK_ADD_WITH_DRAFT', { detail: draft }));
				} catch (err) {
					console.error(err);
					alertError('임시 일정 불러오기 실패');
				}
			});
			top.appendChild(btn);
		}
	});
}



function drawHighlightsOnDays() {
	document.querySelectorAll('.fc-daygrid-day').forEach(dayCell => {
		const dateStr = dayCell.getAttribute('data-date'); // yyyy-MM-dd
		if (!dateStr) return;

		// 기존 표시 제거
		const old = dayCell.querySelector('.day-highlight-pin');
		if (old) old.remove();

		const item = _hlByDate[dateStr];
		if (!item) return;

		const numEl = dayCell.querySelector('.fc-daygrid-day-number');
		if (!numEl) return;

		// 핀 생성 (색상 클래스 또는 HEX 대응)
		const pin = document.createElement('span');
		pin.className = `day-highlight-pin symbol-${item.symbol}`;

		// color: 키워드(red|yellow|blue|orange)면 클래스, 그 외(HEX 등)는 style.color로 직접 지정
		const color = (item.color || '').toLowerCase();
		if (['red', 'yellow', 'blue', 'orange'].includes(color)) {
			pin.classList.add(`color-${color}`);
		} else if (color) {
			pin.style.color = color; // ex) #ff66cc
		} else {
			pin.classList.add('color-red'); // fallback
		}

		pin.title = item.note || '특별한 날';

		// 클릭 시 편집창 열기
		pin.addEventListener('click', (e) => {
			e.stopPropagation();
			openHighlightPicker(dateStr);
		});

		// inline SVG로 심볼 렌더
		renderHighlightSVG(pin, item.symbol);

		// 숫자 컨테이너 기준으로 배치 (숫자 z-index가 더 높아서 항상 보임)
		numEl.style.position = 'relative';
		numEl.appendChild(pin);
	});
}


function symbolToChar(symbol) {
	switch (symbol) {
		case 'star': return '★';
		case 'square': return '■';
		case 'triangle': return '▲';
		case 'circle':
		default: return '●';
	}
}
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

// 하이라이트
function wireDayNumberClick() {
	document.querySelectorAll('.fc-daygrid-day-number').forEach(numEl => {
		// 중복 바인딩 방지
		if (numEl.dataset.hlBound === '1') return;
		numEl.dataset.hlBound = '1';
		numEl.addEventListener('click', (e) => {
			e.preventDefault();
			e.stopPropagation();
			const dayCell = numEl.closest('.fc-daygrid-day');
			const dateStr = dayCell?.getAttribute('data-date');
			if (!dateStr) return;
			openHighlightPicker(dateStr);
		});
	});
}

// 하이라이트 편집창 함수
async function openHighlightPicker(dateStr) {
	const cur = _hlByDate[dateStr] || { symbol: 'circle', color: 'red', note: '' };

	const html = `
    <div style="text-align:left;display:flex;flex-direction:column;gap:10px">
      <div><b>${dateStr}</b> 특별 표시</div>
      <div>
        <div style="margin-bottom:6px">기호</div>
        <label><input type="radio" name="hlSymbol" value="circle"  ${cur.symbol === 'circle' ? 'checked' : ''}> ● (동그라미)</label><br/>
        <label><input type="radio" name="hlSymbol" value="star"    ${cur.symbol === 'star' ? 'checked' : ''}> ★ (별)</label><br/>
        <label><input type="radio" name="hlSymbol" value="square"  ${cur.symbol === 'square' ? 'checked' : ''}> ■ (네모)</label><br/>
        <label><input type="radio" name="hlSymbol" value="triangle" ${cur.symbol === 'triangle' ? 'checked' : ''}> ▲ (세모)</label>
      </div>
      <div>
        <div style="margin-bottom:6px">색상</div>
        <label><input type="radio" name="hlColor" value="red"    ${cur.color === 'red' ? 'checked' : ''}> 빨강</label>
        <label><input type="radio" name="hlColor" value="yellow" ${cur.color === 'yellow' ? 'checked' : ''} style="margin-left:10px"> 노랑</label>
        <label><input type="radio" name="hlColor" value="blue"   ${cur.color === 'blue' ? 'checked' : ''} style="margin-left:10px"> 파랑</label>
        <label><input type="radio" name="hlColor" value="orange" ${cur.color === 'orange' ? 'checked' : ''} style="margin-left:10px"> 주황</label>
      </div>
      <div>
        <div style="margin-bottom:6px">메모(옵션)</div>
        <input id="hlNote" class="swal2-input" placeholder="툴팁으로 표시될 메모" value="${cur.note?.replace(/"/g, '&quot;') || ''}" />
      </div>
    </div>
  `;

	const { isConfirmed, isDenied } = await Swal.fire({
		title: '하이라이트',
		html,
		showDenyButton: !!_hlByDate[dateStr],
		denyButtonText: '삭제',
		showCancelButton: true,
		confirmButtonText: '저장'
	});

	if (isDenied) {
		await fetchWithCsrf(`/api/day-highlights/${dateStr}`, { method: 'DELETE' });
		delete _hlByDate[dateStr];
		drawHighlightsOnDays();
		Swal.fire({ icon: 'success', text: '삭제되었습니다.' });
		return;
	}
	if (!isConfirmed) return;

	const container = Swal.getHtmlContainer();
	const symbol = container.querySelector('input[name="hlSymbol"]:checked')?.value || 'circle';
	const color = container.querySelector('input[name="hlColor"]:checked')?.value || 'red';
	const note = container.querySelector('#hlNote')?.value || '';

	await fetchWithCsrf(`/api/day-highlights/${dateStr}`, {
		method: 'PUT',
		body: JSON.stringify({ symbol, color, note })
	});

	_hlByDate[dateStr] = { symbol, color, note };
	drawHighlightsOnDays();
	Swal.fire({ icon: 'success', text: '저장되었습니다.' });
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
			setTimeout(() => injectPlusButtons(), 0);
			// 임시저장 뱃지 갱신
			if (typeof refreshTempBadges === 'function') {
				try { await refreshTempBadges(); } catch (e) { /* noop */ }
			}
			try { await refreshDayHighlights(); } catch (e) { console.warn(e); }
			// 날짜 숫자 클릭 바인딩(한 번만)
			wireDayNumberClick();

		},

		// 선택 모드일 때는 클릭이 '선택 토글', 평소엔 수정 모달 열기
		eventClick: (info) => {
			if (!info || !info.event || !info.event.id) return;
			if (_selectionModeOn) return; // 선택 모드에선 클릭 토글만(decorate 쪽에서 처리)
			openEditModal(info.event.id);
		},
		eventDidMount: (info) => {
			setTimeout(() => injectPlusButtons(), 0);
			// ✅ outline 스타일 적용
			if (info.event.extendedProps && info.event.extendedProps.isOutline) {
				// 클래스 부여해서 CSS에서 강제 스타일
				info.el.classList.add('fc-event-outline');
			}
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
	await loadTempDrafts();        // 사이드바 임시 저장 칩 갱신
	await refreshTempBadges();     // 달력 날짜 뱃지 갱신
	wireSearchBox();
	// 플러스 버튼 주입(최초)
	setTimeout(() => injectPlusButtons(), 0);
});

// ------------------ 데이터 로드 & 렌더 ------------------
export const refreshEvents = async () => {
	try {
		const schedules = await fetchWithCsrf('/api/schedule');
		_allSchedulesRaw = schedules || [];
		if (!schedules) return;

		_allEvents = schedules.map(s => {
			const rawColor = s.color_tag || '#3788d8';
			const isOutline = (rawColor === 'outline');

			return {
				id: s.schedule_id,
				title: s.title,
				start: s.start_time,
				end: s.end_time,

				// ✅ outline이면 캘린더에 강한 배경색 주지 않음
				color: isOutline ? 'transparent' : rawColor,

				allDay: !!s.is_all_day,
				display: 'block',

				// 기본 텍스트색: outline은 나중에 CSS로 덮어쓸 거라 여기선 그냥 흰색 넣어둬도 됨
				textColor: isOutline ? '#000000' : '#ffffff',

				extendedProps: {
					description: s.description || '',
					emoji: s.emoji || null,
					isAllDay: !!s.is_all_day,
					category: (s.category || '').toLowerCase(),
					highlightType: (s.highlight_type || '').toLowerCase(),

					// ✅ outline 여부를 플래그로 넘김
					isOutline: isOutline
				}
			};
		});

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
				const sD = formValues.start ? new Date(formValues.start + 'T00:00:00') : null;
				const eD = formValues.end ? new Date(formValues.end + 'T23:59:59') : null;
				if (sD && sdt < sD) return false;
				if (eD && sdt > eD) return false;
				return true;
			})();
			if (!inRange) return false;

			// 키워드
			const kw = formValues.keyword;
			if (kw) {
				const title = (s.title || '').toLowerCase();
				const desc = (s.description || '').toLowerCase();
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
