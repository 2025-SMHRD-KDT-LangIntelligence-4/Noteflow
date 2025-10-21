import { fetchWithCsrf, alertError } from './schedule-utils.js';
import { initDropdowns, initColorDropdown } from './schedule-ui-dropdown.js';
// [개선]: schedule-quick-add.js에서 export한 함수들을 import하여 사용
import { injectPlusButtons, openQuickAddModal, closeQuickAddModal } from './schedule-quick-add.js';

let calendar;

// schedule-quick-add.js에 정의된 함수를 사용하기 위해 전역 함수로 노출
// (schedule-quick-add.js가 type="module" 없이 로드된다면 window.injectPlusButtons로 노출)
// 현재는 두 파일 모두 type="module"이므로, import/export를 사용하거나,
// schedule-quick-add.js에서 injectPlusButtons를 전역 객체(window)에 할당해야 합니다.
// 여기서는 FullCalendar의 datesSet 콜백에서 전역 함수를 사용하도록 처리합니다.

document.addEventListener('DOMContentLoaded', async () => {
	initDropdowns(); // schedule-ui-dropdown.js의 함수 호출
	initColorDropdown(); // schedule-ui-dropdown.js의 함수 호출
	await loadCalendar();
	// [추가]: 임시 저장 목록 초기 로드
	// [에러때문에 잠시 주석] await loadTempSchedules(); 
	window.calendar = calendar;
	window.refreshEvents = refreshEvents; // quick-add.js에서 사용하므로 유지
	const searchInput = document.getElementById('scheduleSearchInput');
	const searchButton = document.getElementById('searchButton');
	const categoryList = document.getElementById('categoryFilterList');
	if (searchButton) {
        searchButton.addEventListener('click', () => {
            // TODO: 검색어(searchInput.value)를 사용하여 캘린더 이벤트 필터링 로직 구현
            alert(`검색 실행: ${searchInput.value}`);
        });
    }
    
    if (categoryList) {
        categoryList.addEventListener('click', (e) => {
            const tag = e.target.closest('.category-tag');
            if (tag) {
                // TODO: data-filter 값을 사용하여 캘린더 이벤트 필터링 로직 구현
                document.querySelectorAll('.category-tag').forEach(t => t.classList.remove('active'));
                tag.classList.add('active');
                alert(`필터 적용: ${tag.dataset.filter}`);
            }
        });
    }
});

const loadCalendar = async () => {
	const calendarEl = document.getElementById('calendar');
	calendar = new FullCalendar.Calendar(calendarEl, {
		locale: 'ko',
		initialView: 'dayGridMonth',
		selectable: true,
		editable: true,
		eventDisplay: 'block',
		
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
		
		// [개선]: window 대신 import한 함수 직접 호출
		datesSet: (info) => { 
			injectPlusButtons();
		},

		eventClick: async (info) => openEditModal(info.event), // '편집 모달'은 구현 전이라 가정
		
		// [개선]: select 콜백에서 import한 함수 직접 호출
		select: (selectionInfo) => { 
			// select: 드래그 선택 시 호출
			openQuickAddModal(selectionInfo.startStr); 
		}, 
		
		events: {
			url: '/api/schedule/period',
			method: 'GET',
			failure: () => alertError('일정 데이터를 불러오지 못했습니다.'),
		},
	});
	calendar.render();
	await refreshEvents();
	
	// [개선]: import한 함수 직접 호출
	injectPlusButtons();
};
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
    
export const refreshEvents = async () => {
	try {
		const schedules = await fetchWithCsrf('/api/schedule');
		if (!schedules) return;
		
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

