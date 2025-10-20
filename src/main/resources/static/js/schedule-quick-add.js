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
const qaSave = document.getElementById('qaSave');
const qaCancel = document.getElementById('qaCancel');
const qaQuickAddCard = document.querySelector('.quick-add-card'); // 이벤트 버블링 차단용

// 추가 옵션 관련 DOM
const qaToggleAdvanced = document.getElementById('qaToggleAdvanced');
const qaAdvancedOptions = document.getElementById('qaAdvancedOptions');


// ------------------------------ 1. 유틸리티 및 API 호출 함수 ------------------------------

// 시간 입력 필드 토글을 위한 함수
function toggleTimeInputs(isAllDay) {
    const timeRows = document.querySelector('.time-rows');
    if (!timeRows) return;
    timeRows.style.display = isAllDay ? 'none' : 'flex';
}

// 임시 저장 데이터 수집/확인
function collectTempData() {
    if (!qaTitle) return null; // DOM 참조 실패 시 방어 코드

    const isAllDay = qaAllDay.checked;
    
    const payload = {
        title: qaTitle.value.trim(),
        description: qaDesc.value.trim(),
        colorTag: qaColor.value,
        isAllDay: isAllDay,
        startTime: qaStartDate.value + 'T' + (isAllDay ? '00:00:00' : qaStartTime.value + ':00'),
        endTime: qaEndDate.value + 'T' + (isAllDay ? '23:59:59' : qaEndTime.value + ':00'),
        notifyMinutesBefore: qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10),
        // ... 기타 필드 추가
    };
    
    // 제목, 설명 중 하나라도 데이터가 있으면 '작성 중'으로 간주합니다.
    const hasContent = payload.title || payload.description;
    
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
        
        // 임시 저장 성공 시 임시 일정 UI 목록 갱신 (manager.js에서 import)
        if (typeof loadTempSchedules === 'function') {
            loadTempSchedules();
        }

    } catch (err) {
        throw err; // 상위 로직에서 catch하도록 다시 throw
    }
}

// ------------------------------ 2. Modal/UI Export 함수 (최상위 레벨) ------------------------------

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
			openQuickAddModal(btn.dataset.date); // export 함수 호출
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
    }, { once: true }); // 한번만 실행되도록 { once: true } 사용
};


// ------------------------------ 3. 이벤트 핸들러 (최상위 레벨) ------------------------------

// ESC 키 핸들러
function handleEscClose(e) {
    if (e.key === 'Escape' && quickModal && !quickModal.classList.contains('hidden')) {
        // [수정]: Esc 키는 임시 저장 없이 즉시 닫기
        closeQuickAddModal();
    }
}

// 모달 외부 클릭 핸들러 (임시 저장 로직 포함)
function handleOutsideClick(e) {
    if (quickModal.classList.contains('hidden')) return; 

    // 클릭된 요소가 모달 컨테이너 자체가 아니며, 모달 컨테이너의 자식 요소도 아닐 때 (모달 외부 클릭)
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
        // ... (qaSave 로직 유지: Full Save 로직)
        const payload = {
            title: qaTitle.value.trim() || '(제목 없음)',
            description: qaDesc.value.trim(),
            colorTag: qaColor.value,
            isAllDay: qaAllDay.checked,
            startTime: qaStartDate.value + 'T' + (qaAllDay.checked ? '00:00:00' : qaStartTime.value + ':00'),
            endTime: qaEndDate.value + 'T' + (qaAllDay.checked ? '23:59:59' : qaEndTime.value + ':00'),
            notifyMinutesBefore: qaNotify.value === 'custom' ? null : parseInt(qaNotify.value, 10)
        };

        try {
            const res = await fetchWithCsrf('/api/schedule/create', {
                method: 'POST',
                body: JSON.stringify(payload)
            });

            if (!res.ok) throw new Error('일정 생성 실패');

            alertSuccess('일정이 성공적으로 생성되었습니다.');
            
            // 임시 저장 목록에 해당 내용이 남아 있다면, 임시 저장 UI 갱신 (삭제는 백엔드에서 처리 가정)
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