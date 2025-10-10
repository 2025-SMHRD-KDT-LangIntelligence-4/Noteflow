/**
 * schedule-manager.js
 * 스케줄 입력, 삭제, 수정, 검색, 색상 필터, FullCalendar 연동
 */
document.addEventListener('DOMContentLoaded', () => {
    // ------------------------------
    // 0. CSRF 토큰 가져오기 (header.html에서 설정된 meta)
    // ------------------------------
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    // ------------------------------
    // 1. DOM 엘리먼트 참조
    // ------------------------------
    const dateEl = document.querySelector('.date');
    const scheduleEl = document.querySelector('.schedule');
    const inputEl = document.querySelector('.schedule-create');
    const searchEl = document.querySelector('.schedule-search');

    // ------------------------------
    // 2. 상태 변수
    // ------------------------------
    let selectedDate = null;
    let schedulesMap = {};
    let selectedFilter = "all";
    let selectedColor = "#3788d8"; // 기본 색상

    // ------------------------------
    // 3. 색상 필터 클릭 이벤트
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
    // 4. FullCalendar 초기화
    // ------------------------------
    const calendar = new FullCalendar.Calendar(document.getElementById('calendar'), {
        initialView: 'dayGridMonth',
        locale: 'ko',
        headerToolbar: { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek' },
        events: fetchEvents,
        dateClick: async function(info) {
            selectedDate = info.dateStr;
            const clickedDate = new Date(info.dateStr);
            dateEl.innerText = `${clickedDate.getMonth() + 1}월 ${clickedDate.getDate()}일`;
            renderSchedules();
            highlightSelectedDate(info.dayEl);
        }
    });

    calendar.render();

    // ------------------------------
    // 5. 헬퍼 함수
    // ------------------------------
    function highlightSelectedDate(dayEl) {
        document.querySelectorAll('.fc-daygrid-day').forEach(cell => cell.classList.remove('selected-date'));
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

    async function fetchEvents(info, successCallback, failureCallback) {
        try {
            const start = info.startStr;
            const end = info.endStr;
            const response = await fetch(`/api/schedule/period?start=${start}&end=${end}`, {
                method: 'GET',
                headers: { [csrfHeader]: csrfToken }
            });
            const events = await response.json();
            schedulesMap = {};
            events.forEach(s => {
                const key = s.startTime.slice(0,10);
                if (!schedulesMap[key]) schedulesMap[key] = [];
                schedulesMap[key].push(s);
            });
            successCallback(events.map(e => ({
                id: e.scheduleId,
                title: e.title,
                start: e.startTime,
                end: e.endTime,
                backgroundColor: e.colorTag || '#3788d8'
            })));
        } catch (err) {
            failureCallback(err);
        }
    }

    // ------------------------------
    // 6. 일정 추가 (포커스 기반 enter 이벤트)
    // ------------------------------
    inputEl.addEventListener('keydown', async (e) => {
        if (document.activeElement !== inputEl) return;
        if (e.key !== 'Enter' || !inputEl.value.trim()) return;
        if (!selectedDate) {
            alert("먼저 날짜를 선택해주세요.");
            return;
        }

        const scheduleObj = {
            title: inputEl.value.trim(),
            startTime: selectedDate + "T00:00:00",
            endTime: selectedDate + "T23:59:59",
            colorTag: selectedColor
        };

        try {
            const res = await fetch('/api/schedule/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify(scheduleObj)
            });
            if (!res.ok) throw new Error("스케줄 생성 실패");

            inputEl.value = '';
            calendar.refetchEvents();
            renderSchedules();
        } catch (err) {
            console.error(err);
            alert("스케줄 등록 중 오류가 발생했습니다.");
        }
    });

    // ------------------------------
    // 7. 일정 삭제
    // ------------------------------
    window.deleteSchedule = async function(scheduleId) {
        if (!confirm("정말 삭제하시겠습니까?")) return;
        await fetch(`/api/schedule/delete/${scheduleId}`, {
            method: 'DELETE',
            headers: { [csrfHeader]: csrfToken }
        });
        calendar.refetchEvents();
        renderSchedules();
    }

    // ------------------------------
    // 8. 일정 수정
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
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(scheduleObj)
        });
        calendar.refetchEvents();
        renderSchedules();
    }

    // ------------------------------
    // 9. 검색 기능 (포커스 기반 enter 이벤트)
    // ------------------------------
    searchEl.addEventListener('keydown', async (e) => {
        if (document.activeElement !== searchEl) return;
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
    // 10. 색상 선택
    // ------------------------------
    document.querySelectorAll('.color-option').forEach(option => {
        option.addEventListener('click', () => {
            document.querySelectorAll('.color-option').forEach(o => o.classList.remove('selected'));
            option.classList.add('selected');
            selectedColor = option.getAttribute('data-color');
        });
    });

    // ------------------------------
    // 11. FullCalendar 이벤트 렌더링 커스터마이징
    // ------------------------------
    calendar.refetchEvents = function() {
        calendar.removeAllEvents();
        Object.keys(schedulesMap).forEach(date => {
            schedulesMap[date].forEach(event => {
                if (selectedFilter === "all" || event.colorTag === selectedFilter) {
                    calendar.addEvent({
                        title: event.title,
                        start: event.startTime,
                        end: event.endTime,
                        color: event.colorTag || "#3788d8"
                    });
                }
            });
        });
    };

}); // DOMContentLoaded 종료
