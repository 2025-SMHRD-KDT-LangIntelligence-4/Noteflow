import { fetchWithCsrf, alertSuccess, alertError } from './schedule-utils.js';
// [가정 반영]: schedule-manager.js에서 loadTempSchedules를 export 했다고 가정하고 import
// [에러 때문에 잠시 주석 처리] import { loadTempSchedules } from './schedule-manager.js'; 

// ------------------------------ 모듈 스코프 DOM 참조 (최상위) ------------------------------
// DOMContentLoaded 실행 전 null일 수 있지만, DOM이 로드된 후 사용됩니다.
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
const qaEmoji = document.getElementById('qaEmoji'); // 추가
const qaSave = document.getElementById('qaSave');
const qaCancel = document.getElementById('qaCancel');
const qaQuickAddCard = document.querySelector('.quick-add-card');

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

// ------------------------------ 1. 유틸리티 및 API 호출 함수 ------------------------------

// 시간 입력 필드 토글을 위한 함수
function toggleTimeInputs(isAllDay) {
	const timeRows = document.querySelector('.time-rows');
	if (!timeRows) return;
	timeRows.style.display = isAllDay ? 'none' : 'flex';
}

// 최종 저장용 데이터 수집 함수
function collectData() {
	if (!qaTitle) return null;

	const isAllDay = qaAllDay.checked;

	// 1. 알림 분 값 가져오기
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
	
	// highlightType ENUM 값 처리: 빈 값이면 'none' 사용
	const highlightValue = qaHighlightType ? qaHighlightType.value.trim() : null;
	const safeHighlightValue = (highlightValue === "" || highlightValue === null)
		? 'none' // 'none'은 ENUM 타입에 정의된 유효한 값
		: highlightValue;

	const payload = {
		// [필수 및 기본 필드]
		title: qaTitle.value.trim(),
		description: qaDesc.value.trim(),
		colorTag: qaColor.value,
		isAllDay: isAllDay,

		// [시간/날짜 필드]
		startTime: qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
		endTime: qaEndDate.value + 'T' + (isAllDay ? '23:59:59' : qaEndTime.value + ':00'),

		// [알림 시간]
		alarmTime: alarmTimeString,

		// [추가 옵션 필드]
		emoji: qaEmoji ? qaEmoji.value.trim() : null,
		alertType: qaAlertType ? qaAlertType.value.trim() : null,
		customAlertValue: qaCustomAlertValue ? (qaCustomAlertValue.value || null) : null,
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

	// 필드 초기화
	qaTitle.value = '';
	qaDesc.value = '';
	qaColor.value = '#3788d8';
	qaAllDay.checked = false;
	qaNotify.value = '0';
	if (qaEmoji) qaEmoji.value = '';

	if (qaAdvancedOptions) qaAdvancedOptions.classList.add('hidden');
	if (qaToggleAdvanced) qaToggleAdvanced.textContent = '추가 옵션 보기';

	const today = dateStr ? new Date(dateStr) : new Date();
	const dateISO = today.toISOString().slice(0, 10);

	qaStartDate.value = dateStr || dateISO;
	qaEndDate.value = dateStr || dateISO;
	qaStartTime.value = '09:00';
	qaEndTime.value = '10:00';

	toggleTimeInputs(qaAllDay.checked);

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

// 모달 외부 클릭 핸들러 (임시 저장 로직 제거)
function handleOutsideClick(e) {
	if (quickModal.classList.contains('hidden')) return;

	const isClickOutside = !quickModal.contains(e.target);

	if (isClickOutside) {
		closeQuickAddModal(); // 임시 저장 로직 없이 바로 닫기
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

	// 5. 저장 버튼 이벤트
	qaSave.addEventListener('click', async () => {
		const payload = collectData();

		if (!payload.title) {
			payload.title = "(제목 없음)";
		}

		try {
			// [수정]: fetchWithCsrf는 이미 JSON 데이터를 반환합니다. createdSchedule은 Response 객체가 아닙니다.
			const createdSchedule = await fetchWithCsrf('/api/schedule/create', {
				method: 'POST',
				body: JSON.stringify(payload)
			});

			// fetchWithCsrf가 응답에 문제가 있으면 여기서 이미 throw를 던집니다.
			// 따라서 !res.ok 검사 및 res.json() 호출은 불필요합니다.
			
			// createdSchedule이 null인 경우는 서버가 204 No Content를 보냈을 경우입니다.
			console.log('일정 생성 성공, 응답 데이터:', createdSchedule);
			
			alertSuccess('일정이 성공적으로 생성되었습니다.');

			// 캘린더 이벤트 갱신 (성공 시에만 실행)
			if (window.refreshEvents && typeof window.refreshEvents === 'function') {
				await window.refreshEvents();
			} else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
				window.calendar.refetchEvents();
			}

			closeQuickAddModal();
		} catch (err) {
			console.error(err);
			// [수정]: undefined 에러 방지를 위해 err.message를 안전하게 출력합니다.
			const errorMessage = err.message || '알 수 없는 오류 (네트워크 문제 또는 응답 처리 오류)';
			alertError(`일정 생성 중 오류가 발생했습니다. (${errorMessage})`);
		}
		
		// 🚨 [제거된 부분]: 이전 코드에서 try...catch 블록 바깥에 중복으로 존재하던
		// 이벤트 갱신 및 모달 닫기 로직을 제거했습니다.
	});

	qaCancel.addEventListener('click', closeQuickAddModal);

	// 6. 전역 이벤트 리스너 등록
	document.addEventListener('keydown', handleEscClose);
	document.addEventListener('click', handleOutsideClick);
});