/**
 * schedule-manager.js
 * 스케줄 입력, 삭제, 수정, 검색, 색상 필터, FullCalendar 연동 (자동 조회버전)
 */
document.addEventListener('DOMContentLoaded', async () => {
    // ------------------------------
    // 0. CSRF 토큰
    // ------------------------------
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    // ------------------------------
    // 1. DOM 엘리먼트 참조
    // ------------------------------
    const dateEl = document.querySelector('.date');
    const scheduleEl = document.querySelector('.schedule');
    const inputEl = document.querySelector('.schedule-create');
    const searchEl = document.querySelector('.schedule-search');
    const rangeToggle = document.getElementById('rangeModeToggle');
    const rangeInputs = document.getElementById('rangeInputs');
    const rangePickerInput = document.getElementById('rangePicker');

    // ------------------------------
    // 2. 상태 변수
    // ------------------------------
    let selectedDate = null;
    let schedulesMap = {};
    let selectedFilter = "all";
    let selectedColor = "#3788d8"; // 기본 색상
    let isRangeMode = false;
    let rangePicker = null;

    // ------------------------------
    // 3. FullCalendar 초기화
    // ------------------------------
    const calendarEl = document.getElementById('calendar');
    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        selectable: true,
        locale: 'ko',
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek'
        },
        views: {
            dayGridMonth: { displayEventTime: false },
            timeGridWeek: { 
                displayEventTime: true,
                eventTimeFormat: { hour: '2-digit', minute: '2-digit', meridiem: false }
            }
        },
        events: async function(info, successCallback, failureCallback) {
            try {
                const start = info.startStr;
                const end = info.endStr;

                const res = await fetch(`/api/schedule/period?start=${start}&end=${end}`, {
                    method: 'GET',
                    headers: { [csrfHeader]: csrfToken }
                });
                const data = await res.json();
                schedulesMap = {};

                // 날짜별 스케줄 맵핑
                data.forEach(s => {
                    const key = s.startTime.slice(0,10);
                    if (!schedulesMap[key]) schedulesMap[key] = [];
                    schedulesMap[key].push(s);
                });

                // FullCalendar 이벤트 생성
                successCallback(data.map(e => ({
                    id: e.scheduleId,
                    title: e.title,
                    start: e.startTime,
                    end: e.endTime,
                    backgroundColor: e.colorTag || '#3788d8',
                    colorTag: e.colorTag || '#3788d8'
                })));

                if (selectedDate) renderSchedules();
            } catch (err) {
                console.error("이벤트 로딩 실패:", err);
                failureCallback(err);
            }
        },
        eventDidMount: function(info) {
            const colorTag = info.event.extendedProps.colorTag || info.event.backgroundColor || '#3788d8';
            info.el.style.color = colorTag;
        },
        dateClick: function(info) {
            if (!isRangeMode) {
                selectedDate = info.dateStr;
                const clickedDate = new Date(info.dateStr);
                dateEl.innerText = `${clickedDate.getMonth() + 1}월 ${clickedDate.getDate()}일`;
                renderSchedules();
                highlightSelectedDate(info.dayEl);
            }
        }
    });

    calendar.render();
    await calendar.refetchEvents();

    // ------------------------------
    // 4. 기간 선택 모드 초기화
    // ------------------------------
    rangeToggle.addEventListener('change', () => {
        isRangeMode = rangeToggle.checked;
        rangeInputs.style.display = isRangeMode ? 'block' : 'none';

        if (isRangeMode && !rangePicker) {
            rangePicker = flatpickr(rangePickerInput, {
                mode: 'range',
                dateFormat: 'Y-m-d',
                locale: 'ko'
            });
        }
    });

    // ------------------------------
    // 5. 헬퍼 함수
    // ------------------------------
    function highlightSelectedDate(dayEl) {
        document.querySelectorAll('.fc-daygrid-day').forEach(cell =>
            cell.classList.remove('selected-date')
        );
        dayEl.classList.add('selected-date');
    }

    function renderSchedules() {
        const key = selectedDate;
        if (!key || !schedulesMap[key]) {
            scheduleEl.innerText = "작성된 스케줄 없음";
            return;
        }
        let list = schedulesMap[key];
        if (selectedFilter !== "all") {
            list = list.filter(item => item.colorTag === selectedFilter);
        }
        scheduleEl.innerHTML = list.map(item => `
            <span style="display:flex; align-items:center; gap:4px;">
                <span style="width:12px; height:12px; background-color:${item.colorTag}; border-radius:50%;"></span>
                ${item.title} 
                <button onclick="editSchedule(${item.scheduleId})">수정</button> 
                <button onclick="deleteSchedule(${item.scheduleId})">삭제</button>
            </span>
        `).join("<br>");
    }

    // ------------------------------
    // 6. 색상 필터 클릭
    // ------------------------------
    document.querySelectorAll('.filter-option').forEach(option => {
        option.addEventListener('click', () => {
            document.querySelectorAll('.filter-option').forEach(o => o.classList.remove('selected'));
            option.classList.add('selected');
            selectedFilter = option.getAttribute('data-color');
            renderSchedules();
            calendar.refetchEvents();
        });
    });

    // ------------------------------
    // 7. 일정 추가
    // ------------------------------
    inputEl.addEventListener('keydown', async (e) => {
        if (e.key !== 'Enter' || !inputEl.value.trim()) return;

        let scheduleObj = { title: inputEl.value.trim(), colorTag: selectedColor };

        if (isRangeMode && rangePicker && rangePicker.selectedDates.length === 2) {
            scheduleObj.startTime = rangePicker.selectedDates[0].toISOString().split('T')[0] + "T00:00:00";
            scheduleObj.endTime = rangePicker.selectedDates[1].toISOString().split('T')[0] + "T23:59:59";
        } else {
            if (!selectedDate) {
                alert("먼저 날짜를 선택해주세요.");
                return;
            }
            scheduleObj.startTime = selectedDate + "T00:00:00";
            scheduleObj.endTime = selectedDate + "T23:59:59";
        }

        try {
            const res = await fetch('/api/schedule/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
                body: JSON.stringify(scheduleObj)
            });
            if (!res.ok) throw new Error("스케줄 생성 실패");

            inputEl.value = '';
            if (rangePicker) rangePicker.clear();
            await calendar.refetchEvents();
            renderSchedules();
        } catch (err) {
            console.error(err);
            alert("스케줄 등록 중 오류 발생");
        }
    });

    // ------------------------------
    // 8. 일정 삭제
    // ------------------------------
    window.deleteSchedule = async function(scheduleId) {
        if (!confirm("정말 삭제하시겠습니까?")) return;
        await fetch(`/api/schedule/delete/${scheduleId}`, {
            method: 'DELETE',
            headers: { [csrfHeader]: csrfToken }
        });
        await calendar.refetchEvents();
        renderSchedules();
    }

    // ------------------------------
    // 9. 일정 수정
    // ------------------------------
    window.editSchedule = async function(scheduleId) {
        const newTitle = prompt("일정 제목을 수정하세요:");
        if (!newTitle) return;
        const scheduleObj = {
            title: newTitle,
            startTime: selectedDate + "T00:00:00",
            endTime: selectedDate + "T23:59:59"
        };
        await fetch(`/api/schedule/update/${scheduleId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
            body: JSON.stringify(scheduleObj)
        });
        await calendar.refetchEvents();
        renderSchedules();
    }

    // ------------------------------
    // 10. 검색 기능
    // ------------------------------
    searchEl.addEventListener('keydown', async (e) => {
        if (e.key !== 'Enter') return;
        const keyword = searchEl.value.trim();
        if (!keyword) return;

        try {
            const response = await fetch(`/api/schedule/search?keyword=${keyword}`, {
                method: 'GET',
                headers: { [csrfHeader]: csrfToken }
            });
            const results = await response.json();
            alert("검색 결과: " + results.map(r => r.title).join(", "));
        } catch (err) {
            console.error(err);
            alert("검색 중 오류가 발생했습니다.");
        }
    });

    // ------------------------------
    // 11. 색상 선택
    // ------------------------------
    document.querySelectorAll('.color-option').forEach(option => {
        option.addEventListener('click', () => {
            document.querySelectorAll('.color-option').forEach(o => o.classList.remove('selected'));
            option.classList.add('selected');
            selectedColor = option.getAttribute('data-color');
        });
    });

});
