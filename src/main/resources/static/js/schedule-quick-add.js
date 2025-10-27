// /Noteflow/src/main/resources/static/js/schedule-quick-add.js
import { fetchWithCsrf, alertSuccess, alertError, fetchWithCsrfAndFiles } from './schedule-utils.js';
import { openMapModal } from './schedule-map.js';

// ------------------------------ 모듈 스코프 DOM 참조 (최상위) ------------------------------
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
const qaEmoji = document.getElementById('qaEmoji');
const qaSave = document.getElementById('qaSave');
const qaCancel = document.getElementById('qaCancel');
const qaQuickAddCard = document.querySelector('.quick-add-card');
const qaTempSave = document.getElementById('qaTempSave');

// ✅ 반복 일정 및 관련 DOM 참조
const qaRepeat = document.getElementById('qaRepeat');
const repeatOptionLabel = document.getElementById('repeatOptionLabel');
const repeatWarningContainer = document.getElementById('repeatWarningContainer'); // 반복 경고 멘트 컨테이너

// ✅ 알림 커스텀 관련 DOM
const qaCustomAlertContainer = document.getElementById('qaCustomAlertContainer');

// 추가 옵션 관련 DOM
const qaToggleAdvanced = document.getElementById('qaToggleAdvanced');
const qaAdvancedOptions = document.getElementById('qaAdvancedOptions');
const qaAlertType = document.getElementById('qaAlertType');
const qaCustomAlertValue = document.getElementById('qaCustomAlertValue');
const qaLocation = document.getElementById('qaLocation');
const qaHighlightType = document.getElementById('qaHighlightType');
const qaCategory = document.getElementById('qaCategory');
const qaAttachmentPath = document.getElementById('qaAttachmentPath');
const qaAttachmentList = document.getElementById('qaAttachmentList');

const qaCategoryAddBtn = document.getElementById('qaCategoryAddBtn');
const qaCategoryTags = document.getElementById('qaCategoryTags');

let qaCategoryValues = []; // ['java','python', ...] ← 이 배열이 진짜 소스오브트루스
const qaFileUploader = document.getElementById('qaFileUploader');
const qaAttachmentListSlot = document.getElementById('qaAttachmentListSlot');

let _currentTempId = null; // 임시초안에서 열린 경우 temp_id 기억


// ------------------------------ 카테고리 칩 렌더링 / 동기화 ------------------------------
function renderQaCategoryTags() {
	// 화면 칩들 다시 그림
	if (qaCategoryTags) {
		qaCategoryTags.innerHTML = '';
		qaCategoryValues.forEach((v, idx) => {
			const tag = document.createElement('span');
			tag.className = 'category-tag active';
			tag.style.display = 'inline-flex';
			tag.style.alignItems = 'center';
			tag.style.gap = '6px';
			tag.textContent = v;

			const x = document.createElement('button');
			x.type = 'button';
			x.textContent = 'X';
			x.style.marginLeft = '6px';
			x.className = 'btn small';
			x.onclick = () => {
				qaCategoryValues.splice(idx, 1);
				renderQaCategoryTags();
			};

			tag.appendChild(x);
			qaCategoryTags.appendChild(tag);
		});
	}

	// ⭐ FIX: 숨겨진 실제 input 값(qaCategory.value)도 항상 qaCategoryValues 기준으로 갱신
	// 이 값이 collectData() -> payload.category 로 그대로 전송된다.
	if (qaCategory) {
		qaCategory.value = qaCategoryValues.join(',');
	}
}

// 사용자가 입력칸에 값 적고 [+ 추가] 눌렀을 때 호출
function tryAddQaCategory() {
	const v = (qaCategory?.value || '').trim();
	if (!v) return;
	if (!qaCategoryValues.includes(v)) {
		qaCategoryValues.push(v);
	}
	// 입력칸을 비우고 다시 렌더 (렌더가 qaCategory.value를 join()으로 세팅해줌)
	renderQaCategoryTags();
}


// ------------------------------ 파일 업로드/첨부 유틸 ------------------------------
async function handleFileUpload(e, slotElement, pathInput, listInput) {
	const files = e.target.files;
	if (!files.length) return;

	for (const file of files) {
		const formData = new FormData();
		formData.append('file', file);

		try {
			const result = await fetchWithCsrfAndFiles('/api/schedule-files/upload', formData);
			addFileToSlot(result.fileName, result.filePath, slotElement);
		} catch (err) {
			alertError(`'${file.name}' 업로드 실패: ` + err.message);
		}
	}

	updateHiddenAttachmentInputs(slotElement, pathInput, listInput);
	e.target.value = null; // 동일 파일 다시 가능하도록 초기화
}

function handleFileDelete(e, slotElement, pathInput, listInput) {
	if (e.target.classList.contains('file-delete-btn')) {
		const fileItem = e.target.closest('.file-item');

		// TODO: 필요하다면 서버에도 삭제 요청 보내기
		fileItem.remove();

		updateHiddenAttachmentInputs(slotElement, pathInput, listInput);
		alertSuccess('파일 목록에서 제거되었습니다. (서버 삭제 필요)');
	}
}

function addFileToSlot(fileName, filePath, slotElement) {
	const fileItem = document.createElement('div');
	fileItem.className = 'file-item';
	fileItem.dataset.path = filePath;
	fileItem.innerHTML = `${fileName} <span class="file-delete-btn" title="목록에서 제거">X</span>`;
	slotElement.appendChild(fileItem);
}

function updateHiddenAttachmentInputs(slotElement, pathInput, listInput) {
	const items = [];
	slotElement.querySelectorAll('.file-item').forEach(item => {
		items.push({
			fileName: item.textContent.replace(/ X$/, ''), // "X" 제거
			filePath: item.dataset.path
		});
	});

	pathInput.value = items.length > 0 ? items[0].filePath : '';
	listInput.value = JSON.stringify(items);
}


// ------------------------------ 시간/반복 옵션/알림 옵션 UI ------------------------------
function toggleTimeInputs(isAllDay) {
	const timeRows = document.querySelector('.time-rows');
	if (!timeRows) return;

	// 하루종일이면 시간 필드 숨김
	timeRows.style.display = isAllDay ? 'none' : 'flex';

	// 반복 옵션 표시 여부 (멀티데이 & not all-day 일 때만)
	if (repeatOptionLabel && qaRepeat) {
		const startDateValue = qaStartDate.value;
		const endDateValue = qaEndDate.value;

		if (startDateValue && endDateValue) {
			const startDate = new Date(startDateValue);
			const endDate = new Date(endDateValue);

			if (!isAllDay && startDate.toDateString() !== endDate.toDateString()) {
				repeatOptionLabel.style.display = 'flex';
			} else {
				repeatOptionLabel.style.display = 'none';
				qaRepeat.checked = false;
			}
		} else {
			repeatOptionLabel.style.display = 'none';
			qaRepeat.checked = false;
		}
	}

	// 경고 문구도 같이 업데이트
	toggleRepeatWarning();
}

// 반복 경고 멘트 표시/숨김
function toggleRepeatWarning() {
	if (qaRepeat && repeatWarningContainer) {
		const isRepeatOptionVisible =
			repeatOptionLabel && repeatOptionLabel.style.display === 'flex';

		if (qaRepeat.checked && isRepeatOptionVisible) {
			repeatWarningContainer.style.display = 'block';
		} else {
			repeatWarningContainer.style.display = 'none';
		}
	}
}

// 알림 "사용자 정의" 선택 시 커스텀 필드 노출
function toggleCustomAlertFields() {
	if (!qaNotify || !qaCustomAlertContainer) return;

	if (qaNotify.value === 'custom') {
		qaCustomAlertContainer.style.display = 'flex';
	} else {
		qaCustomAlertContainer.style.display = 'none';
	}
}


// ------------------------------ 백엔드로 보낼 payload 구성 ------------------------------
function collectData() {
	const isAllDay = qaAllDay.checked;

	// 알림 분(드롭다운)
	const notifyMinutesBefore =
		qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10);

	// alarmTime 계산
	const startDateTimeString =
		qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00');

	let alarmTimeString = null;
	if (notifyMinutesBefore !== null && notifyMinutesBefore >= 0 && startDateTimeString) {
		const startDateTime = new Date(startDateTimeString);

		if (notifyMinutesBefore > 0) {
			startDateTime.setMinutes(startDateTime.getMinutes() - notifyMinutesBefore);
		}

		const year = startDateTime.getFullYear();
		const month = String(startDateTime.getMonth() + 1).padStart(2, '0');
		const day = String(startDateTime.getDate()).padStart(2, '0');
		const hours = String(startDateTime.getHours()).padStart(2, '0');
		const minutes = String(startDateTime.getMinutes()).padStart(2, '0');
		const seconds = String(startDateTime.getSeconds()).padStart(2, '0');

		alarmTimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
	}

	// 알림 타입(checkbox 여러 개) → CSV 문자열
	const selectedAlertTypes = [];
	document.querySelectorAll('input[name="qaAlertType"]:checked').forEach(cb => {
		if (!cb.disabled) {
			selectedAlertTypes.push(cb.value);
		}
	});
	const alertTypeValue = selectedAlertTypes.length > 0 ? selectedAlertTypes.join(',') : '0';

	// highlightType (비어있으면 'none')
	const highlightValue = qaHighlightType ? qaHighlightType.value.trim() : null;
	const safeHighlightValue =
		(highlightValue === '' || highlightValue === null) ? 'none' : highlightValue;

	const payload = {
		title: qaTitle.value.trim(),
		description: qaDesc.value.trim(),
		colorTag: qaColor.value,
		isAllDay: isAllDay,

		startTime: qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
		endTime: qaEndDate.value + 'T' + (isAllDay ? '23:59:59' : qaEndTime.value + ':00'),

		alarmTime: alarmTimeString,

		emoji: qaEmoji ? qaEmoji.value.trim() : null,
		alertType: alertTypeValue,
		customAlertValue: qaCustomAlertValue ? (qaCustomAlertValue.value || null) : null,
		location: qaLocation ? qaLocation.value.trim() : null,
		mapLat: null,
		mapLng: null,
		highlightType: safeHighlightValue,

		// ⭐ FIX: category는 qaCategory.value (renderQaCategoryTags가 항상 동기화해줌)
		category: qaCategory ? qaCategory.value.trim() : null,

		attachmentPath: qaAttachmentPath ? qaAttachmentPath.value.trim() : null,
		attachmentList: qaAttachmentList
			? (qaAttachmentList.value.trim() || '[]')
			: '[]',
	};

	return payload;
}


// ------------------------------ 모달 열고 닫기 ------------------------------

// 드래프트(임시 저장)으로부터 모달 오픈
// ⭐ FIX: draft.category를 qaCategoryValues에 반영하고 renderQaCategoryTags()로 동기화
window.openQuickAddFromDraft = function(draft) {
	if (!draft) return;

	// 기준 날짜 추출해서 모달 연다
	const baseDate = draft.start_time || draft.startTime || null;
	openQuickAddModal(baseDate ? String(baseDate).slice(0, 10) : null);

	_currentTempId = draft.temp_id || draft.tempId || null;

	const s = draft.start_time || draft.startTime;
	const e = draft.end_time || draft.endTime;
	const isAllDay = !!(draft.is_all_day ?? draft.isAllDay);

	qaTitle.value = draft.title || '';
	qaDesc.value = draft.description || '';
	qaColor.value = draft.color_tag || draft.colorTag || 'outline';
	qaAllDay.checked = isAllDay;

	if (s) {
		qaStartDate.value = s.slice(0, 10);
		qaStartTime.value = isAllDay ? '00:00' : (s.slice(11, 16) || '09:00');
	}
	if (e) {
		qaEndDate.value = e.slice(0, 10);
		qaEndTime.value = isAllDay ? '23:59' : (e.slice(11, 16) || '10:00');
	}

	if (qaEmoji) qaEmoji.value = draft.emoji || '';
	if (qaLocation) qaLocation.value = draft.location || '';
	if (qaHighlightType) qaHighlightType.value = draft.highlight_type || draft.highlightType || 'none';

	// ⭐ FIX: draft.category -> qaCategoryValues -> 렌더
	const draftCategoryStr = draft.category || '';
	qaCategoryValues = draftCategoryStr
		.split(',')
		.map(v => v.trim())
		.filter(Boolean);
	renderQaCategoryTags();

	if (qaAttachmentPath) qaAttachmentPath.value = draft.attachment_path || draft.attachmentPath || '';
	if (qaAttachmentList) qaAttachmentList.value = draft.attachment_list || draft.attachmentList || '[]';

	if (qaAttachmentListSlot) {
		qaAttachmentListSlot.innerHTML = '';
		try {
			const items = JSON.parse(qaAttachmentList.value || '[]');
			items.forEach(it => {
				const div = document.createElement('div');
				div.className = 'file-item';
				div.dataset.path = it.filePath;
				div.innerHTML = `${it.fileName} <span class="file-delete-btn" title="목록에서 제거">X</span>`;
				qaAttachmentListSlot.appendChild(div);
			});
		} catch { /* ignore JSON parse error */ }
	}

	// UI 동기화
	toggleTimeInputs(qaAllDay.checked);
	toggleCustomAlertFields();
};


// 진짜로 새 일정 추가 모달 열기 (캘린더 +버튼 등에서 호출)
export function openQuickAddModal(dateStr) {
	if (!quickModal) return;

	_currentTempId = null; // 새로 여는 거니까 draft 상태 해제

	// 기본 값 초기화
	qaTitle.value = '';
	qaDesc.value = '';
	qaColor.value = 'outline';
	qaAllDay.checked = false;
	qaNotify.value = '0';
	if (qaEmoji) qaEmoji.value = '';
	if (qaRepeat) qaRepeat.checked = false;

	// 추가 옵션 영역 초기화
	if (qaAdvancedOptions) qaAdvancedOptions.classList.add('hidden');
	if (qaToggleAdvanced) qaToggleAdvanced.textContent = '추가 옵션 보기';

	// 알림 관련
	if (qaCustomAlertValue) qaCustomAlertValue.value = '';
	document.querySelectorAll('input[name="qaAlertType"]').forEach(cb => {
		cb.checked = false;
	});

	// 위치/하이라이트/첨부 초기화
	if (qaLocation) qaLocation.value = '';
	if (qaHighlightType) qaHighlightType.value = '';
	if (qaAttachmentPath) qaAttachmentPath.value = '';
	if (qaAttachmentList) qaAttachmentList.value = '';
	if (qaAttachmentListSlot) qaAttachmentListSlot.innerHTML = '';

	// ⭐ FIX: 카테고리 상태 완전히 초기화
	qaCategoryValues = [];
	renderQaCategoryTags(); // ← chips 영역 비우고 qaCategory.value도 ''로 맞춤

	const today = dateStr ? new Date(dateStr) : new Date();
	const dateISO = today.toISOString().slice(0, 10);

	qaStartDate.value = dateStr || dateISO;
	qaEndDate.value = dateStr || dateISO;
	qaStartTime.value = '09:00';
	qaEndTime.value = '10:00';

	// UI 동기화
	toggleTimeInputs(qaAllDay.checked);
	toggleCustomAlertFields(); // 커스텀 알림 필드 초기 상태
	toggleRepeatWarning(); // 반복 경고 문구 초기화

	// 모달 표시 + 애니메이션
	quickModal.classList.remove('hidden');
	quickModal.setAttribute('aria-hidden', 'false');

	quickModal.style.opacity = 0;
	quickModal.style.transform = 'translateY(-20px) translateX(-50%)';
	requestAnimationFrame(() => {
		quickModal.style.transition = 'all 0.25s ease-out';
		quickModal.style.opacity = 1;
		quickModal.style.transform = 'translateY(0) translateX(-50%)';
	});
}

// 모달 닫기
export function closeQuickAddModal() {
	if (!quickModal) return;

	quickModal.style.opacity = 0;
	quickModal.style.transform = 'translateY(-20px) translateX(-50%)';

	quickModal.addEventListener('transitionend', function handler(e) {
		if (e.propertyName === 'opacity') {
			quickModal.classList.add('hidden');
			quickModal.setAttribute('aria-hidden', 'true');
			quickModal.removeEventListener('transitionend', handler);
		}
	}, { once: true });
}


// ------------------------------ ESC/바깥클릭 핸들러 ------------------------------
function handleEscClose(e) {
	if (e.key === 'Escape' && quickModal && !quickModal.classList.contains('hidden')) {
		closeQuickAddModal();
	}
}

function handleOutsideClick(e) {
	if (!quickModal || quickModal.classList.contains('hidden')) return;

	// 지도 모달 열려있으면 닫기 금지
	if (window.__MAP_MODAL_OPEN) return;

	// 지도 모달 내부 클릭이면 무시
	const inMapModal = e.target.closest && e.target.closest('#kakaoMapModal');
	if (inMapModal) return;

	// quick-add 카드 기준으로 닫기 여부 판별
	const cardEl = qaQuickAddCard || quickModal.querySelector('.quick-add-card');
	if (!cardEl) return;

	const clickedInsideCard = cardEl.contains(e.target);
	const clickedInsideOverlay = quickModal.contains(e.target);

	if (!clickedInsideCard && clickedInsideOverlay) {
		closeQuickAddModal();
	}
}
export function injectPlusButtons() {
   if (!quickModal) return;

   document.querySelectorAll('.fc-daygrid-day').forEach(dayCell => {
      const dayTop = dayCell.querySelector('.fc-daygrid-day-top');
      if (!dayTop || dayTop.querySelector('.day-plus-btn')) return;

      const btn = document.createElement('button');
      btn.className = 'day-plus-btn';
      btn.type = 'button';
      btn.title = '일정 추가';
      btn.innerText = '+';

      const dateStr = dayCell.getAttribute('data-date') || '';
      if (dateStr) btn.dataset.date = dateStr;

      btn.addEventListener('click', (e) => {
         e.stopPropagation();
         openQuickAddModal(btn.dataset.date);
      });

      dayTop.appendChild(btn);
   });
}

// yyyy-MM-dd 문자열(startYmd)부터 yyyy-MM-dd 문자열(endYmd)까지
// 하루씩 증가시키며 ["2025-10-25","2025-10-26", ...] 형태 배열을 리턴
function getDateRangeInclusive(startYmd, endYmd) {
	const result = [];
	if (!startYmd || !endYmd) return result;

	const start = new Date(startYmd + 'T00:00:00');
	const end = new Date(endYmd + 'T00:00:00');

	for (let cur = new Date(start); cur <= end; cur.setDate(cur.getDate() + 1)) {
		const y = cur.getFullYear();
		const m = String(cur.getMonth() + 1).padStart(2, '0');
		const d = String(cur.getDate()).padStart(2, '0');
		result.push(`${y}-${m}-${d}`);
	}
	return result;
}

// ------------------------------ DOMContentLoaded에서 리스너 묶기 ------------------------------
document.addEventListener('DOMContentLoaded', () => {

	// 처음엔 모달 숨겨둠
	if (quickModal) quickModal.classList.add('hidden');

	// 하루종일 토글
	if (qaAllDay) {
		qaAllDay.addEventListener('change', (e) => {
			toggleTimeInputs(e.target.checked);
		});
	}

	// 날짜 바뀌면 반복 옵션 다시 계산
	if (qaStartDate && qaEndDate && qaAllDay) {
		const handleDateChange = () => toggleTimeInputs(qaAllDay.checked);
		qaStartDate.addEventListener('change', handleDateChange);
		qaEndDate.addEventListener('change', handleDateChange);
	}

	// 반복 체크박스 바뀌면 경고 문구 다시 표시/숨김
	if (qaRepeat) {
		qaRepeat.addEventListener('change', toggleRepeatWarning);
	}

	// 알림 옵션 바뀌면 커스텀 필드 표시/숨김
	if (qaNotify) {
		qaNotify.addEventListener('change', toggleCustomAlertFields);
	}

	// 카테고리 추가 버튼 / 엔터키
	if (qaCategoryAddBtn) qaCategoryAddBtn.addEventListener('click', tryAddQaCategory);
	if (qaCategory) {
		qaCategory.addEventListener('keydown', (e) => {
			if (e.key === 'Enter') {
				e.preventDefault();
				tryAddQaCategory();
			}
		});
	}

	// 파일 업로드/삭제
	if (qaFileUploader) {
		qaFileUploader.addEventListener('change', (e) => {
			handleFileUpload(e, qaAttachmentListSlot, qaAttachmentPath, qaAttachmentList);
		});
	}
	if (qaAttachmentListSlot) {
		qaAttachmentListSlot.addEventListener('click', (e) => {
			handleFileDelete(e, qaAttachmentListSlot, qaAttachmentPath, qaAttachmentList);
		});
	}

	// 지도 버튼
	const qaMapBtn = document.getElementById('qaMapBtn');
	if (qaMapBtn) {
		qaMapBtn.addEventListener('click', () => {
			openMapModal(qaLocation);
		});
	}

	// 추가 옵션 토글
	if (qaToggleAdvanced && qaAdvancedOptions) {
		qaToggleAdvanced.addEventListener('click', () => {
			const isHidden = qaAdvancedOptions.classList.toggle('hidden');
			qaToggleAdvanced.textContent = isHidden ? '추가 옵션 보기' : '추가 옵션 닫기';
		});
	}

	// 모달 카드 내부 클릭 시 버블 방지
	if (qaQuickAddCard) {
		qaQuickAddCard.addEventListener('click', (e) => {
			e.stopPropagation();
		});
	}

	// 임시저장 (신규/덮어쓰기 자동 분기)
	if (qaTempSave) {
		qaTempSave.addEventListener('click', async () => {
			const payload = collectData();
			if (!payload) return;
			if (!payload.title) payload.title = "(제목 없음)";

			try {
				qaTempSave.disabled = true;

				const url = _currentTempId
					? `/api/temp-schedule/${_currentTempId}`
					: `/api/temp-schedule`;

				const saved = await fetchWithCsrf(url, {
					method: 'POST',
					body: JSON.stringify(payload)
				});

				_currentTempId = saved?.temp_id || saved?.tempId || _currentTempId;

				closeQuickAddModal();
				Swal.fire({ icon: 'success', text: saved?.message || '임시 저장되었습니다.' });

				if (window.loadTempDrafts) await window.loadTempDrafts();
				if (window.refreshTempBadges) await window.refreshTempBadges();

			} catch (err) {
				console.error(err);
				Swal.fire({ icon: 'error', text: `임시 저장 실패: ${err?.message || '알 수 없는 오류'}` });
			} finally {
				qaTempSave.disabled = false;
			}
		});
	}

	// 최종 등록 버튼
	if (qaSave) {
		qaSave.addEventListener('click', async () => {
			// 1) 기본 payload 한 번 뽑기
			const basePayload = collectData();
			if (!basePayload.title) {
				basePayload.title = "(제목 없음)";
			}

			// 2) 지금이 "기간 내 동일 시간 반복 생성" 케이스인지 판단
			const isRepeat =
				qaRepeat &&
				qaRepeat.checked &&
				repeatOptionLabel &&
				repeatOptionLabel.style.display === 'flex';

			// helper: alarmTime을 특정 날짜에 맞춰 다시 계산해주는 함수
			const buildAlarmTimeForDate = (ymd) => {
				// qaNotify 기준으로 몇 분 전 알림인지 확인
				const notifyMinutesBefore =
					qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10);

				if (notifyMinutesBefore === null || notifyMinutesBefore < 0) {
					// 'custom'이거나 음수면 여기서는 알람 안 건드림 (basePayload.alarmTime 그대로 쓰거나 null)
					return null;
				}

				// 이벤트 시작 시각 (해당 ymd 사용)
				const startClock = basePayload.isAllDay
					? '00:00:00'
					: (qaStartTime.value + ':00');
				const startDateTimeString = ymd + 'T' + startClock;

				const startDateObj = new Date(startDateTimeString);
				if (notifyMinutesBefore > 0) {
					startDateObj.setMinutes(startDateObj.getMinutes() - notifyMinutesBefore);
				}

				const year = startDateObj.getFullYear();
				const month = String(startDateObj.getMonth() + 1).padStart(2, '0');
				const day = String(startDateObj.getDate()).padStart(2, '0');
				const hours = String(startDateObj.getHours()).padStart(2, '0');
				const minutes = String(startDateObj.getMinutes()).padStart(2, '0');
				const seconds = String(startDateObj.getSeconds()).padStart(2, '0');

				return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
			};

			// 3) 반복 일정 처리 분기
			if (isRepeat) {
				// 여기서부터는 백엔드의 /api/schedule/repeat/add 를 안 쓰고
				// 날짜별로 /api/schedule/create 를 여러 번 호출해서
				// 카테고리(category) 등 모든 필드를 온전히 저장하게 만든다.
				alertSuccess('반복 일정 등록을 시작합니다...');

				try {
					qaSave.disabled = true; // 중복 클릭 방지

					// start/end 기준으로 날짜 배열 뽑기
					const days = getDateRangeInclusive(qaStartDate.value, qaEndDate.value);

					for (const ymd of days) {
						// 하루치 payload를 구성
						const dayPayload = {
							...basePayload,
							startTime: ymd + 'T' + (basePayload.isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
							endTime: ymd + 'T' + (basePayload.isAllDay ? '23:59:59' : qaEndTime.value + ':00'),
							// 날짜별 alarmTime 다시 계산
							alarmTime: buildAlarmTimeForDate(ymd)
						};

						// 실제 단건 생성 호출
						await fetchWithCsrf('/api/schedule/create', {
							method: 'POST',
							body: JSON.stringify(dayPayload)
						});
					}

					// 성공 후 메시지
					alertSuccess('반복 일정이 성공적으로 등록되었습니다.');

					// 캘린더 갱신
					if (window.refreshEvents && typeof window.refreshEvents === 'function') {
						await window.refreshEvents();
					} else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
						window.calendar.refetchEvents();
					}

					// 임시초안(clean-up)
					if (_currentTempId) {
						try {
							await fetchWithCsrf(`/api/temp-schedule/${_currentTempId}`, { method: 'DELETE' });
							if (window.loadTempDrafts) await window.loadTempDrafts();
							if (window.refreshTempBadges) await window.refreshTempBadges();
						} catch (e) {
							console.warn('임시초안 자동 삭제 실패(무시 가능):', e);
						} finally {
							_currentTempId = null;
						}
					}

					closeQuickAddModal();
				} catch (err) {
					console.error(err);
					const errorMessage = err.message || '알 수 없는 오류 (네트워크 문제 또는 응답 처리 오류)';
					alertError(`반복 일정 생성 중 오류가 발생했습니다. (${errorMessage})`);
				} finally {
					qaSave.disabled = false;
				}

				return; // 여기서 종료 (아래 단일 생성 로직은 타지 않음)
			}

			// 4) 일반 단일 일정 생성 로직 (원래 /api/schedule/create 호출 흐름)
			try {
				const createdSchedule = await fetchWithCsrf('/api/schedule/create', {
					method: 'POST',
					body: JSON.stringify(basePayload)
				});

				console.log('일정 생성 성공, 응답 데이터:', createdSchedule);

				alertSuccess('일정이 성공적으로 생성되었습니다.');

				// 캘린더 갱신
				if (window.refreshEvents && typeof window.refreshEvents === 'function') {
					await window.refreshEvents();
				} else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
					window.calendar.refetchEvents();
				}

				// 임시초안에서 온 거라면 자동 삭제 시도
				if (_currentTempId) {
					try {
						await fetchWithCsrf(`/api/temp-schedule/${_currentTempId}`, { method: 'DELETE' });
						if (window.loadTempDrafts) await window.loadTempDrafts();
						if (window.refreshTempBadges) await window.refreshTempBadges();
					} catch (e) {
						console.warn('임시초안 자동 삭제 실패(무시 가능):', e);
					} finally {
						_currentTempId = null;
					}
				}

				closeQuickAddModal();
			} catch (err) {
				console.error(err);
				const errorMessage = err.message || '알 수 없는 오류 (네트워크 문제 또는 응답 처리 오류)';
				alertError(`일정 생성 중 오류가 발생했습니다. (${errorMessage})`);
			}
		});
	
	}

	// 취소 버튼
	if (qaCancel) {
		qaCancel.addEventListener('click', () => {
			_currentTempId = null;
			closeQuickAddModal();
		});
	}

	// 전역 ESC / 바깥 클릭 닫기
	document.addEventListener('keydown', handleEscClose);
	document.addEventListener('click', handleOutsideClick);

	// 초기 상태
	toggleCustomAlertFields();
	toggleRepeatWarning();
});
