import { fetchWithCsrf, alertSuccess, alertError, fetchWithCsrfAndFiles } from './schedule-utils.js';
import { openMapModal } from './schedule-map.js';
// [가정 반영]: schedule-manager.js에서 loadTempSchedules를 export 했다고 가정하고 import
// [에러 때문에 잠시 주석 처리] import { loadTempSchedules } from './schedule-manager.js'; 

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
// ✅ 반복 일정 및 관련 DOM 참조 추가
const qaRepeat = document.getElementById('qaRepeat');
const repeatOptionLabel = document.getElementById('repeatOptionLabel');
const repeatWarningContainer = document.getElementById('repeatWarningContainer'); // ✅ 추가: 경고 멘트 컨테이너

// ✅ 알림 커스텀 관련 DOM 참조 추가
const qaCustomAlertContainer = document.getElementById('qaCustomAlertContainer'); // ✅ 추가: 커스텀 알림 컨테이너

// 추가 옵션 관련 DOM (temp_schedule 컬럼 매핑용)
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
let qaCategoryValues = []; // 내부 상태: ['java','python',...]
const qaFileUploader = document.getElementById('qaFileUploader');
const qaAttachmentListSlot = document.getElementById('qaAttachmentListSlot');

let _currentTempId = null; // ← 지금 모달이 ‘임시초안’에서 열린 경우, 그 temp_id를 기억 


// 드래프트로 모달 열고 값 채우기 + 현재 temp_id 기억
window.openQuickAddFromDraft = function(draft) {
	if (!draft) return;

	// + 모달 기본 오픈 (기준 날짜 추출)
	const baseDate = draft.start_time || draft.startTime || null;
	openQuickAddModal(baseDate ? String(baseDate).slice(0, 10) : null);

	// 임시초안에서 열렸음을 표시
	_currentTempId = draft.temp_id || draft.tempId || null;

	// 값 채우기 (snake/camel 모두 수용)
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
	if (qaCategory) qaCategory.value = draft.category || '';
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
		} catch { }
	}

	toggleTimeInputs(qaAllDay.checked);
	toggleCustomAlertFields();
};

function renderQaCategoryTags() {
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
	// 실제 저장 필드는 콤마 문자열로 유지
	if (qaCategory) qaCategory.value = qaCategoryValues.join(',');
}
function tryAddQaCategory() {
	const v = (qaCategory?.value || '').trim();
	if (!v) return;
	if (!qaCategoryValues.includes(v)) qaCategoryValues.push(v);
	qaCategory.value = ''; // 입력칸 비우기
	renderQaCategoryTags();
}
// ✅ [추가] 파일 업로드 및 목록 관리를 위한 공통 함수 (quick-add, edit 중복 사용)
// (원래는 utils로 가야 하지만, 요청대로 모듈 내에 둡니다)
async function handleFileUpload(e, slotElement, pathInput, listInput) {
	const files = e.target.files;
	if (!files.length) return;

	// (여러 파일 업로드 시 반복 처리 필요)
	for (const file of files) {
		const formData = new FormData();
		formData.append('file', file);

		try {
			const result = await fetchWithCsrfAndFiles('/api/schedule-files/upload', formData);
			// result = { fileName: "강의안.pdf", filePath: "/uploads/uuid.pdf" }

			// 6. 파일 목록 UI 업데이트
			console.log('upload result:', result); // {fileName:"..", filePath:"/uploads/.."}
			addFileToSlot(result.fileName, result.filePath, slotElement);

		} catch (err) {
			alertError(`'${file.name}' 업로드 실패: ` + err.message);
		}
	}
	// 5. 숨겨진 input에 데이터 저장
	updateHiddenAttachmentInputs(slotElement, pathInput, listInput);

	e.target.value = null; // (중요) 동일 파일 다시 업로드 가능하도록 초기화
}

function handleFileDelete(e, slotElement, pathInput, listInput) {
	if (e.target.classList.contains('file-delete-btn')) {
		const fileItem = e.target.closest('.file-item');
		const filePath = e.target.dataset.path; // (file-item의 data-path 사용)

		// (TODO: 서버에서 실제 파일 삭제 API 호출 - 예: /api/files/delete?path=filePath)

		fileItem.remove();
		// 숨겨진 input 업데이트
		updateHiddenAttachmentInputs(slotElement, pathInput, listInput);

		alertSuccess('파일 목록에서 제거되었습니다. (서버 삭제 필요)');
	}
}

function addFileToSlot(fileName, filePath, slotElement) {
	const fileItem = document.createElement('div');
	fileItem.className = 'file-item';
	// data-path를 file-item에 저장
	fileItem.dataset.path = filePath;
	fileItem.innerHTML = `${fileName} <span class="file-delete-btn" title="목록에서 제거">X</span>`;
	slotElement.appendChild(fileItem);
}

function updateHiddenAttachmentInputs(slotElement, pathInput, listInput) {
	const items = [];
	slotElement.querySelectorAll('.file-item').forEach(item => {
		items.push({
			fileName: item.textContent.replace(/ X$/, ''), // "X" 버튼 텍스트 제거
			filePath: item.dataset.path
		});
	});

	// pathInput (첫 번째 파일 경로)
	pathInput.value = items.length > 0 ? items[0].filePath : '';
	// listInput (JSON 문자열)
	listInput.value = JSON.stringify(items);
}
// ------------------------------ 1. 유틸리티 및 API 호출 함수 ------------------------------

// 시간 입력 필드 토글 및 반복 옵션 표시 로직 수정
function toggleTimeInputs(isAllDay) {
	const timeRows = document.querySelector('.time-rows');
	if (!timeRows) return;

	// 1. 하루종일 체크박스에 따른 시간 입력 필드 표시/숨김
	timeRows.style.display = isAllDay ? 'none' : 'flex';

	// 2. 반복 옵션 표시 로직 (사용자 요청에 따라 Warning 토글 로직은 별도 함수로 분리)
	if (repeatOptionLabel && qaRepeat) {

		const startDateValue = qaStartDate.value;
		const endDateValue = qaEndDate.value;

		// 날짜 입력값이 모두 있어야 비교 가능
		if (startDateValue && endDateValue) {
			const startDate = new Date(startDateValue);
			const endDate = new Date(endDateValue);

			// '하루종일'이 아니며, 시작 날짜와 종료 날짜가 다를 때만 '반복 옵션' 표시
			if (!isAllDay && startDate.toDateString() !== endDate.toDateString()) {
				repeatOptionLabel.style.display = 'flex';
			} else {
				repeatOptionLabel.style.display = 'none';
				qaRepeat.checked = false; // 옵션이 숨겨지면 체크 해제
			}
		} else {
			repeatOptionLabel.style.display = 'none';
			qaRepeat.checked = false;
		}
	}

	// 모달 열 때 반복 경고 멘트 초기화 (toggleTimeInputs이 호출되는 곳에서 같이 호출)
	toggleRepeatWarning();
}

// ✅ [추가] 반복 경고 멘트 토글 로직 함수
function toggleRepeatWarning() {
	if (qaRepeat && repeatWarningContainer) {
		// qaRepeat이 체크되었고 repeatOptionLabel이 flex 상태일 때만 표시합니다.
		// repeatOptionLabel이 flex 상태가 아니면 (즉, 반복 옵션 자체가 보이지 않으면) 경고도 숨깁니다.
		const isRepeatOptionVisible = repeatOptionLabel && repeatOptionLabel.style.display === 'flex';

		if (qaRepeat.checked && isRepeatOptionVisible) {
			repeatWarningContainer.style.display = 'block';
		} else {
			repeatWarningContainer.style.display = 'none';
		}
	}
}

// ✅ [추가] 알림 사용자 정의 필드 토글 로직 함수
function toggleCustomAlertFields() {
	if (!qaNotify || !qaCustomAlertContainer) return;

	if (qaNotify.value === 'custom') {
		// 'custom' 선택 시 flex를 사용하여 필드를 표시
		qaCustomAlertContainer.style.display = 'flex';
	} else {
		// 다른 옵션 선택 시 숨김
		qaCustomAlertContainer.style.display = 'none';
	}
}


// 최종 저장용 데이터 수집 함수 (isRepeat 필드 제거, 백엔드는 URL로 구분)
function collectData() {

	const isAllDay = qaAllDay.checked;

	// 1. 알림 분 값 가져오기
	// ✅ 알림 커스텀 로직 반영 (현재는 'custom'이면 null을 반환하도록 기존 로직 유지)
	const notifyMinutesBefore = qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10);

	// 2. 시작 시간 (LocalDateTime) 계산을 위한 Date 객체 준비
	const startDateTimeString = qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00');

	let alarmTimeString = null; // 백엔드로 보낼 최종 alarmTime (LocalDateTime 문자열)

	// 3. 알림 시간이 유효하고 (0 이상) 시작 시간 정보가 있다면 계산
	if (notifyMinutesBefore !== null && notifyMinutesBefore >= 0 && startDateTimeString) {
		const startDateTime = new Date(startDateTimeString);

		// 알림 분만큼 시간을 뺌
		if (notifyMinutesBefore > 0) {
			startDateTime.setMinutes(startDateTime.getMinutes() - notifyMinutesBefore);
		}

		// 로컬 시간을 YYYY-MM-DDTHH:mm:ss 형식으로 포맷
		const year = startDateTime.getFullYear();
		const month = String(startDateTime.getMonth() + 1).padStart(2, '0');
		const day = String(startDateTime.getDate()).padStart(2, '0');
		const hours = String(startDateTime.getHours()).padStart(2, '0');
		const minutes = String(startDateTime.getMinutes()).padStart(2, '0');
		const seconds = String(startDateTime.getSeconds()).padStart(2, '0');

		alarmTimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
	}
	// ✅ 1. 알림 타입(다중 선택) 값 수집
	const selectedAlertTypes = [];
	document.querySelectorAll('input[name="qaAlertType"]:checked').forEach(cb => {
		if (!cb.disabled) { // 비활성화된 옵션은 제외
			selectedAlertTypes.push(cb.value);
		}
	});

	// 요청사항: 0. 없음(기본값)
	// 백엔드 엔티티가 String이므로, 선택된 값이 없으면 '0'을 저장합니다.
	const alertTypeValue = selectedAlertTypes.length > 0 ? selectedAlertTypes.join(',') : '0';

	// highlightType ENUM 값 처리: 빈 값이면 'none' 사용
	const highlightValue = qaHighlightType ? qaHighlightType.value.trim() : null;
	const safeHighlightValue = (highlightValue === "" || highlightValue === null)
		? 'none' // 'none'은 ENUM 타입에 정의된 유효한 값이라고 가정
		: highlightValue;

	const payload = {
		// [필수 및 기본 필드]
		title: qaTitle.value.trim(),
		description: qaDesc.value.trim(),
		colorTag: qaColor.value,
		isAllDay: isAllDay,

		// [시간/날짜 필드] - 반복 일정 등록 시 이 기간이 사용됨
		startTime: qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
		endTime: qaEndDate.value + 'T' + (isAllDay ? '23:59:59' : qaEndTime.value + ':00'),

		// [알림 시간]
		alarmTime: alarmTimeString,

		// [추가 옵션 필드]
		emoji: qaEmoji ? qaEmoji.value.trim() : null,
		alertType: alertTypeValue,


		customAlertValue: qaCustomAlertValue ? (qaCustomAlertValue.value || null) : null, // 알림 값
		location: qaLocation ? qaLocation.value.trim() : null,
		mapLat: null,
		mapLng: null,
		highlightType: safeHighlightValue,
		category: qaCategory ? qaCategory.value.trim() : null,
		attachmentPath: qaAttachmentPath ? qaAttachmentPath.value.trim() : null,
		attachmentList: qaAttachmentList ? (qaAttachmentList.value.trim() || '[]')
			: '[]',
	};

	return payload;
}

// ------------------------------ 2. Modal/UI Export 함수 ------------------------------

// + 버튼 주입
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

// 모달 열기
export function openQuickAddModal(dateStr) {
	if (!quickModal) return;
	_currentTempId = null;   // ← 새로 여는 경우, 이전 temp 편집 상태 해제
	// 필드 초기화
	qaTitle.value = '';
	qaDesc.value = '';
	qaColor.value = 'outline';
	qaAllDay.checked = false;
	qaNotify.value = '0';
	if (qaEmoji) qaEmoji.value = '';
	if (qaRepeat) qaRepeat.checked = false;
	if (qaAdvancedOptions) qaAdvancedOptions.classList.add('hidden');
	if (qaToggleAdvanced) qaToggleAdvanced.textContent = '추가 옵션 보기';

	// 추가 옵션 필드 초기화
	// ✅ 알림 타입 체크박스 초기화
	document.querySelectorAll('input[name="qaAlertType"]').forEach(cb => {
		cb.checked = false;
	});
	if (qaCustomAlertValue) qaCustomAlertValue.value = '';
	if (qaLocation) qaLocation.value = '';
	if (qaHighlightType) qaHighlightType.value = '';
	if (qaCategory) qaCategory.value = '';
	if (qaAttachmentPath) qaAttachmentPath.value = '';
	if (qaAttachmentList) qaAttachmentList.value = '';
	if (qaAttachmentListSlot) qaAttachmentListSlot.innerHTML = '';
	const today = dateStr ? new Date(dateStr) : new Date();
	const dateISO = today.toISOString().slice(0, 10);

	qaStartDate.value = dateStr || dateISO;
	qaEndDate.value = dateStr || dateISO;
	qaStartTime.value = '09:00';
	qaEndTime.value = '10:00';

	// UI 상태 조정
	toggleTimeInputs(qaAllDay.checked);
	toggleCustomAlertFields(); // ✅ 커스텀 알림 필드 초기 상태 설정

	quickModal.classList.remove('hidden');
	quickModal.setAttribute('aria-hidden', 'false');

	// 모달 애니메이션
	quickModal.style.opacity = 0;
	quickModal.style.transform = 'translateY(-20px) translateX(-50%)';
	requestAnimationFrame(() => {
		quickModal.style.transition = 'all 0.25s ease-out';
		quickModal.style.opacity = 1;
		quickModal.style.transform = 'translateY(0) translateX(-50%)';
	});
};

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
};


// ------------------------------ 3. 이벤트 핸들러 ------------------------------

// ESC 키 핸들러
function handleEscClose(e) {
	if (e.key === 'Escape' && quickModal && !quickModal.classList.contains('hidden')) {
		closeQuickAddModal();
	}
}

// 모달 외부 클릭 핸들러: 이제 '퀵 모달 카드' 기준으로 닫기
function handleOutsideClick(e) {
	if (!quickModal || quickModal.classList.contains('hidden')) return;

	// 1) 지도 모달 열려있으면 닫기 동작 막기
	if (window.__MAP_MODAL_OPEN) return;

	// 2) 지도 모달 내부 클릭이면 무시
	const inMapModal = e.target.closest && e.target.closest('#kakaoMapModal');
	if (inMapModal) return;

	// 3) 기준 요소를 '퀵 모달 카드'로 변경
	const cardEl = qaQuickAddCard || quickModal.querySelector('.quick-add-card');
	if (!cardEl) return;

	// 4) 카드 내부 클릭이면 닫지 않음 / 카드 외부 클릭이고, 오버레이(quickModal) 안에서의 클릭이면 닫기
	const clickedInsideCard = cardEl.contains(e.target);
	const clickedInsideOverlay = quickModal.contains(e.target);

	if (!clickedInsideCard && clickedInsideOverlay) {
		closeQuickAddModal();
	}
}


// ------------------------------ 4. DOMContentLoaded (이벤트 리스너 등록) ------------------------------

document.addEventListener('DOMContentLoaded', () => {

	// 1. 초기 화면에서 모달 숨김
	if (quickModal) quickModal.classList.add('hidden');

	// 2. 하루종일 체크박스 토글 이벤트 추가
	if (qaAllDay) {
		qaAllDay.addEventListener('change', (e) => {
			toggleTimeInputs(e.target.checked);
		});
	}

	// ✅ 날짜 입력 필드 변경 시 반복 옵션 가시성 재평가
	if (qaStartDate && qaEndDate && qaAllDay) {
		const handleDateChange = () => toggleTimeInputs(qaAllDay.checked);
		qaStartDate.addEventListener('change', handleDateChange);
		qaEndDate.addEventListener('change', handleDateChange);
	}

	// ✅ [추가] qaRepeat 체크박스 변경 시 경고 멘트 토글
	if (qaRepeat) {
		qaRepeat.addEventListener('change', toggleRepeatWarning);
	}

	// ✅ [추가] qaNotify 드롭다운 변경 시 커스텀 알림 필드 토글
	if (qaNotify) {
		qaNotify.addEventListener('change', toggleCustomAlertFields);
	}
	if (qaCategoryAddBtn) qaCategoryAddBtn.addEventListener('click', tryAddQaCategory);
	if (qaCategory) qaCategory.addEventListener('keydown', (e) => {
		if (e.key === 'Enter') {
			e.preventDefault();
			tryAddQaCategory();
		}
	});
	// ✅ [추가] 파일 업로드 및 삭제 이벤트 리스너 연결
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
	// ✅ [추가] 지도 버튼 이벤트 리스너
	const qaMapBtn = document.getElementById('qaMapBtn');
	if (qaMapBtn) {
		qaMapBtn.addEventListener('click', () => {
			openMapModal(qaLocation); // qaLocation Input 요소를 타겟으로 전달
		});
	}
	// 3. 추가 옵션 토글 이벤트 추가
	if (qaToggleAdvanced && qaAdvancedOptions) {
		qaToggleAdvanced.addEventListener('click', () => {
			const isHidden = qaAdvancedOptions.classList.toggle('hidden');
			qaToggleAdvanced.textContent = isHidden ? '추가 옵션 보기' : '추가 옵션 닫기';
		});
	}

	// 4. 모달 카드 내부 클릭 시 이벤트 버블링 방지 
	if (qaQuickAddCard) {
		qaQuickAddCard.addEventListener('click', (e) => {
			e.stopPropagation();
		});
	}
	// 임시저장 (덮어쓰기/신규 생성 자동 분기)
	if (qaTempSave) {
		qaTempSave.addEventListener('click', async () => {
			const payload = collectData();
			if (!payload) return;
			if (!payload.title) payload.title = "(제목 없음)";

			try {
				// (선택) 중복 클릭 방지
				qaTempSave.disabled = true;
				// 백엔드가 PUT 미지원 → 업데이트도 POST로 보냄
				// 신규:  POST /api/temp-schedule
				// 수정:  POST /api/temp-schedule/{id}
				const url = _currentTempId
					? `/api/temp-schedule/${_currentTempId}`
					: `/api/temp-schedule`;

				const saved = await fetchWithCsrf(url, {
					method: 'POST',
					body: JSON.stringify(payload)
				});

				// 응답으로 temp_id 확보(신규 생성 시 이후부터는 덮어쓰기)
				_currentTempId = saved?.temp_id || saved?.tempId || _currentTempId;

				// ✅ 먼저 모달 닫기
				closeQuickAddModal();
				// 그 다음 토스트(알림)
				Swal.fire({ icon: 'success', text: saved?.message || '임시 저장되었습니다.' });

				// 사이드바/뱃지 갱신
				if (window.loadTempDrafts) await window.loadTempDrafts();
				if (window.refreshTempBadges) await window.refreshTempBadges();

			} catch (err) {
				console.error(err);
				Swal.fire({ icon: 'error', text: `임시 저장 실패: ${err?.message || '알 수 없는 오류'}` });
			} finally {
				// (선택) 버튼 원복
				qaTempSave.disabled = false;
			}

		});
	}
	// 5. 저장 버튼 이벤트
	qaSave.addEventListener('click', async () => {
		const payload = collectData();

		if (!payload.title) {
			payload.title = "(제목 없음)";
		}

		// ✅ 반복 일정 API 엔드포인트 결정
		const isRepeat = qaRepeat && qaRepeat.checked && repeatOptionLabel.style.display === 'flex'; // 옵션이 보일 때만 반복 처리
		const apiUrl = isRepeat ? '/api/schedule/repeat/add' : '/api/schedule/create';

		if (isRepeat) {
			alertSuccess('반복 일정 등록을 시작합니다...'); // 반복 등록은 시간이 좀 걸릴 수 있음을 알림
		}
		try {
			// [수정]: fetchWithCsrf는 이미 JSON 데이터를 반환합니다. createdSchedule은 Response 객체가 아닙니다.
			const createdSchedule = await fetchWithCsrf(apiUrl, {
				method: 'POST',
				body: JSON.stringify(payload)
			});

			console.log('일정 생성 성공, 응답 데이터:', createdSchedule);
			const successMessage = isRepeat
				? '반복 일정이 성공적으로 등록되었습니다.'
				: '일정이 성공적으로 생성되었습니다.';
			alertSuccess(successMessage);

			// 캘린더 갱신 (성공 시)
			if (window.refreshEvents && typeof window.refreshEvents === 'function') {
				await window.refreshEvents();
			} else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
				window.calendar.refetchEvents();
			}

			// ★ 임시초안에서 시작했다면 자동 삭제
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
			// [수정]: undefined 에러 방지를 위해 err.message를 안전하게 출력합니다.
			const errorMessage = err.message || '알 수 없는 오류 (네트워크 문제 또는 응답 처리 오류)';
			alertError(`일정 생성 중 오류가 발생했습니다. (${errorMessage})`);
		}

	});

	qaCancel.addEventListener('click', () => {
		_currentTempId = null;   // 편집 상태 초기화
		closeQuickAddModal();
	});
	// 6. 전역 이벤트 리스너 등록
	document.addEventListener('keydown', handleEscClose);
	document.addEventListener('click', handleOutsideClick);

	// ✅ 초기 상태 설정 (필요한 경우)
	toggleCustomAlertFields();
	toggleRepeatWarning(); // 초기화 시점에 경고 멘트 숨김 보장

});
