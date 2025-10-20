import { fetchWithCsrf, alertSuccess, alertError } from './schedule-utils.js';
// [가정 반영]: schedule-manager.js에서 loadTempSchedules를 export 했다고 가정하고 import
import { loadTempSchedules } from './schedule-manager.js'; 

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
// qaMapBtn (버튼 자체는 데이터 전송에 사용되지 않으므로 참조 생략)

// ------------------------------ 1. 유틸리티 및 API 호출 함수 ------------------------------

// 시간 입력 필드 토글을 위한 함수
function toggleTimeInputs(isAllDay) {
    const timeRows = document.querySelector('.time-rows');
    if (!timeRows) return;
    timeRows.style.display = isAllDay ? 'none' : 'flex';
}

// [수정]: 임시 저장 데이터 수집/확인 - temp_schedule 전체 필드 전송을 위해 확장
function collectTempData() {
    if (!qaTitle) return null; 

    const isAllDay = qaAllDay.checked;
    
    const payload = {
        // [필수 및 기본 필드]
        title: qaTitle.value.trim(),
        description: qaDesc.value.trim(),
        colorTag: qaColor.value,
        isAllDay: isAllDay,
        
        // [시간/날짜 필드] (T 포맷으로 백엔드 전송)
        startTime: qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
        endTime: qaEndDate.value + 'T' + (isAllDay ? '23:59:59' : qaEndTime.value + ':00'),

        // [알림 필드] (notifyMinutesBefore는 alarm_time 필드에 매핑될 값)
        alarmTime: qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10), // alarm_time 컬럼 대응
        
        // [추가 옵션 필드] (temp_schedule 컬럼에 대응)
        emoji: qaEmoji ? qaEmoji.value.trim() : null, 
        alertType: qaAlertType ? qaAlertType.value.trim() : null,
        customAlertValue: qaCustomAlertValue ? (qaCustomAlertValue.value || null) : null,
        customAlertUnit: null, // HTML에 없는 필드. 백엔드에서 처리하거나, 필요 시 HTML/JS에 추가해야 함
        
        location: qaLocation ? qaLocation.value.trim() : null,
        mapLat: null, // 지도 연동 로직 부재로 null
        mapLng: null, // 지도 연동 로직 부재로 null
        
        highlightType: qaHighlightType ? qaHighlightType.value.trim() : null,
        category: qaCategory ? qaCategory.value.trim() : null,
        attachmentPath: qaAttachmentPath ? qaAttachmentPath.value.trim() : null,
        attachmentList: qaAttachmentList ? qaAttachmentList.value.trim() : null,
    };
    
    // 제목, 설명, 위치 중 하나라도 데이터가 있으면 '작성 중'으로 간주
    const hasContent = payload.title || payload.description || payload.location; 
    
    return hasContent ? payload : null;
}

// 임시 저장 API 호출 함수
async function saveTemporarySchedule(payload) {
    try {
        const res = await fetchWithCsrf('/api/schedule/temp-save', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errorText = await res.text();
            throw new Error(`임시 저장 API 오류: ${res.status} - ${errorText}`);
        }
        
        // 임시 저장 성공 시 임시 일정 UI 목록 갱신
        if (typeof loadTempSchedules === 'function') {
            loadTempSchedules();
        }

    } catch (err) {
        throw err; 
    }
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
    // 추가 옵션 필드 초기화 (필요하다면)

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

// 모달 외부 클릭 핸들러 (임시 저장 로직 포함)
function handleOutsideClick(e) {
    if (quickModal.classList.contains('hidden')) return; 

    const isClickOutside = !quickModal.contains(e.target);
    
    if (isClickOutside) {
        const dataToSave = collectTempData();
        
        if (dataToSave) {
            saveTemporarySchedule(dataToSave)
                .then(() => {
                    alertSuccess('임시 저장되었습니다.');
                })
                .catch(err => {
                    console.error('임시 저장 실패:', err);
                    alertError('임시 저장 중 오류가 발생했습니다.');
                })
                .finally(() => {
                    closeQuickAddModal();
                });
        } else {
            closeQuickAddModal();
        }
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

    // 4. 모달 카드 내부 클릭 시 이벤트 버블링 방지 (외부 클릭 로직을 위해 필수)
    if (qaQuickAddCard) {
        qaQuickAddCard.addEventListener('click', (e) => {
            e.stopPropagation(); 
        });
    }

    // 5. 저장/취소 버튼 이벤트
    qaSave.addEventListener('click', async () => {
        // [수정]: 전체 필드를 가져오도록 collectTempData 재활용
        const payload = collectTempData(); 

        // 유효성 검사 (제목 필수 등)
        if (!payload || !payload.title) {
            alertError('제목은 필수로 입력해야 합니다.');
            return;
        }

        // is_all_day가 체크되었어도 시간 필드가 전송되는 문제를 해결하기 위해 payload를 조정
        const finalPayload = {
            ...payload,
            // Full Save 시에는 알림 시간 필드를 alarmTime 대신 notifyMinutesBefore로 전달하거나,
            // 백엔드에서 alarmTime 필드에 notifyMinutesBefore를 매핑하도록 해야 합니다.
            notifyMinutesBefore: payload.alarmTime // 임시 저장 필드를 최종 저장 필드로 재활용
        };
        delete finalPayload.alarmTime; // 중복 필드 삭제

        try {
            const res = await fetchWithCsrf('/api/schedule/create', {
                method: 'POST',
                body: JSON.stringify(finalPayload)
            });

            if (!res.ok) throw new Error('일정 생성 실패');

            alertSuccess('일정이 성공적으로 생성되었습니다.');
            
            // 캘린더 이벤트 갱신
            if (window.refreshEvents && typeof window.refreshEvents === 'function') {
                await window.refreshEvents();
            } else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
                window.calendar.refetchEvents();
            }

            closeQuickAddModal();
        } catch (err) {
            console.error(err);
            alertError('일정 생성 중 오류가 발생했습니다.');
        }
    });

    qaCancel.addEventListener('click', closeQuickAddModal);
    
    // 6. 전역 이벤트 리스너 등록
    document.addEventListener('keydown', handleEscClose);
    document.addEventListener('click', handleOutsideClick); 
});