/**
 * schedule-manager.js
 * 스케줄 입력, 삭제, 수정, 검색, 색상 필터, FullCalendar 연동
 * - 시간 입력(type="time") 연동
 * - 기간 모드: 전체적용(각일별 생성) / 비활성(연속 일정 생성)
 * - FullCalendar end 보정: 과보정 제거 (루프 -1초 유지)
 */
document.addEventListener('DOMContentLoaded', async () => {
    // ------------------------------
    // 0. CSRF 토큰
    // ------------------------------
    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    // ------------------------------
    // 1. DOM
    // ------------------------------
    const dateEl = document.querySelector('.date');
    const scheduleEl = document.querySelector('.schedule');
    const inputEl = document.querySelector('.schedule-create');
    const searchEl = document.querySelector('.schedule-search');
    const rangeToggle = document.getElementById('rangeModeToggle');
    const rangeInputs = document.getElementById('rangeInputs');
    const rangePickerInput = document.getElementById('rangePicker');

    // time inputs
    const startTimeInput = document.getElementById('startTime');
    const endTimeInput = document.getElementById('endTime');
    const applyToAllDaysInput = document.getElementById('applyToAllDays');
    const multiStartInput = document.getElementById('multiStartTime');
    const multiEndInput = document.getElementById('multiEndTime');
    const rangeStartInput = document.getElementById('rangeStartTime');
    const rangeEndInput = document.getElementById('rangeEndTime');

    // ------------------------------
    // 2. state
    // ------------------------------
    let selectedDate = null;
    let schedulesMap = {};
    let selectedFilter = "all";
    let selectedColor = "#3788d8";
    let isRangeMode = false;
    let rangePicker = null;

    // ------------------------------
    // 3. FullCalendar init
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
                if (!res.ok) throw new Error("일정 불러오기 실패");
                const data = await res.json();

                // reset map
                schedulesMap = {};

                // map by local date (include multi-day events)
                data.forEach(s => {
                    const start = s.startTime ? new Date(s.startTime) : null;
                    const end = s.endTime ? new Date(s.endTime) : start;
                    if (!start) return;

                    // loopEndRaw = end - 1 second (so if end was stored as "2025-10-20T23:59:59" it counts 20th)
                    const loopEndRaw = new Date(end);
                    loopEndRaw.setSeconds(loopEndRaw.getSeconds() - 1);

                    let cur = startOfDayLocal(start);
                    const last = startOfDayLocal(loopEndRaw);

                    for (let d = new Date(cur); d <= last; d.setDate(d.getDate() + 1)) {
                        const key = toLocalDateKey(d);
                        if (!schedulesMap[key]) schedulesMap[key] = [];
                        schedulesMap[key].push(s);
                    }
                });

                // Build FullCalendar events (no +1day overcorrection)
                const events = data.map(e => {
                    const s = e.startTime ? new Date(e.startTime) : null;
                    const en = e.endTime ? new Date(e.endTime) : null;

                    return {
                        id: e.scheduleId,
                        title: e.title,
                        start: s ? s.toISOString() : null,
                        end: en ? en.toISOString() : null,
                        // don't force allDay; let FullCalendar decide (we use timed events)
                        allDay: false,
                        backgroundColor: e.colorTag || '#3788d8',
                        colorTag: e.colorTag || '#3788d8'
                    };
                });

                successCallback(events);

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
    // 4. rangePicker 초기화 (flatpickr)
    // ------------------------------
    rangeToggle.addEventListener('change', () => {
        isRangeMode = rangeToggle.checked;
        rangeInputs.style.display = isRangeMode ? 'block' : 'none';

        // show/hide multi-day UI parts
        const multiDayOption = document.getElementById("multiDayOption");
        const singleDayTime = document.getElementById("singleDayTime");
        if (isRangeMode) {
            singleDayTime.style.display = 'none';
            multiDayOption.style.display = 'block';
        } else {
            singleDayTime.style.display = 'block';
            multiDayOption.style.display = 'none';
        }

        if (isRangeMode && !rangePicker) {
            rangePicker = flatpickr(rangePickerInput, {
                mode: 'range',
                dateFormat: 'Y-m-d',
                locale: 'ko'
            });
        }
    });

    // ------------------------------
    // 5. helpers
    // ------------------------------
    function highlightSelectedDate(dayEl) {
        document.querySelectorAll('.fc-daygrid-day').forEach(cell =>
            cell.classList.remove('selected-date')
        );
        dayEl.classList.add('selected-date');
    }

    function toLocalDateKey(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    }

    function startOfDayLocal(date) {
        const r = new Date(date);
        r.setHours(0,0,0,0);
        return r;
    }

    // combine dateStr "YYYY-MM-DD" and timeStr "HH:MM" -> ISO local string "YYYY-MM-DDTHH:MM:SS"
    function makeLocalDateTimeISO(dateStr, timeStr, fallbackTime) {
        const t = timeStr && timeStr.trim() ? timeStr.trim() : fallbackTime;
        const secs = t.length === 5 ? ":00" : ""; // if "HH:MM" add seconds
        return `${dateStr}T${t}${secs}`;
    }

    // clamp valid time "HH:MM" (returns "HH:MM" or default)
    function normalizeTimeInput(timeStr, defaultStr="01:00") {
        if (!timeStr || !timeStr.trim()) return defaultStr;
        const parts = timeStr.split(':');
        if (parts.length < 2) return defaultStr;
        let hh = parseInt(parts[0], 10);
        let mm = parseInt(parts[1], 10);
        if (Number.isNaN(hh) || Number.isNaN(mm)) return defaultStr;
        // clamp
        if (hh < 0) hh = 0;
        if (hh > 23) hh = 23;
        if (mm < 0) mm = 0;
        if (mm > 59) mm = 59;
        // round to nearest 10 minutes
        mm = Math.round(mm / 10) * 10;
        if (mm === 60) { mm = 0; hh = (hh + 1) % 24; }
        return `${String(hh).padStart(2,'0')}:${String(mm).padStart(2,'0')}`;
    }

    // iterate inclusive dates between startDateStr and endDateStr (YYYY-MM-DD)
    function iterateDatesInclusive(startDateStr, endDateStr) {
        const list = [];
        const s = new Date(startDateStr);
        const e = new Date(endDateStr);
        let d = new Date(s);
        d.setHours(0,0,0,0);
        e.setHours(0,0,0,0);
        while (d <= e) {
            list.push(d.toISOString().slice(0,10));
            d.setDate(d.getDate() + 1);
        }
        return list;
    }

    // ------------------------------
    // 6. renderSchedules (left panel)
    // ------------------------------
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
        scheduleEl.innerHTML = list.map(item => {
            const startTimeStr = item.startTime ? (new Date(item.startTime)).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : '';
            const endTimeStr = item.endTime ? (new Date(item.endTime)).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : '';
            const timeLabel = startTimeStr ? `(${startTimeStr}${endTimeStr ? ' - ' + endTimeStr : ''}) ` : '';
            return `
                <div style="display:flex; align-items:center; gap:8px; margin-bottom:6px;">
                    <span style="width:12px; height:12px; background-color:${item.colorTag}; border-radius:50%;"></span>
                    <span>${timeLabel}${item.title}</span>
                    <button onclick="editSchedule(${item.scheduleId})">수정</button>
                    <button onclick="deleteSchedule(${item.scheduleId})">삭제</button>
                </div>
            `;
        }).join("<br>");
    }

    // ------------------------------
    // 7. color filter
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
    // 8. schedule create (Enter)
    // ------------------------------
    inputEl.addEventListener('keydown', async (e) => {
        if (e.key !== 'Enter' || !inputEl.value.trim()) return;

        const title = inputEl.value.trim();
        const colorTag = selectedColor;

        // helper defaults and normalized times
        const defaultStart = "01:00";
        const defaultEnd = "23:00";

        // If range mode and two dates selected
        if (isRangeMode && rangePicker && rangePicker.selectedDates.length === 2) {
            const d0 = rangePicker.selectedDates[0].toISOString().slice(0,10);
            const d1 = rangePicker.selectedDates[1].toISOString().slice(0,10);

            // applyToAllDays: create separate events per day with same times
            if (applyToAllDaysInput && applyToAllDaysInput.checked) {
                const ms = normalizeTimeInput(multiStartInput.value, defaultStart);
                const me = normalizeTimeInput(multiEndInput.value, defaultEnd);
                const dates = iterateDatesInclusive(d0, d1);

                // build payloads per date
                const calls = dates.map(dateStr => {
                    const payload = {
                        title,
                        colorTag,
                        startTime: makeLocalDateTimeISO(dateStr, ms, defaultStart) + ":00",
                        endTime: makeLocalDateTimeISO(dateStr, me, defaultEnd) + ":59"
                    };
                    return fetch('/api/schedule/create', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
                        body: JSON.stringify(payload)
                    });
                });

                try {
                    const results = await Promise.all(calls);
                    const ok = results.every(r => r.ok);
                    if (!ok) throw new Error('일부 일정 생성 실패');
                    inputEl.value = '';
                    rangePicker.clear();
                    await calendar.refetchEvents();
                    renderSchedules();
                } catch (err) {
                    console.error(err);
                    alert("일정(전체적용) 생성 중 오류 발생");
                }
            } else {
                // not applyToAllDays: create single continuous schedule from startDate+rangeStartTime -> endDate+rangeEndTime
                const rs = normalizeTimeInput(rangeStartInput.value, defaultStart);
                const re = normalizeTimeInput(rangeEndInput.value, defaultEnd);
                const payload = {
                    title,
                    colorTag,
                    startTime: makeLocalDateTimeISO(d0, rs, defaultStart) + ":00",
                    endTime: makeLocalDateTimeISO(d1, re, defaultEnd) + ":59"
                };
                try {
                    const res = await fetch('/api/schedule/create', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
                        body: JSON.stringify(payload)
                    });
                    if (!res.ok) throw new Error('스케줄 생성 실패');
                    inputEl.value = '';
                    rangePicker.clear();
                    await calendar.refetchEvents();
                    renderSchedules();
                } catch (err) {
                    console.error(err);
                    alert("스케줄 등록 중 오류 발생");
                }
            }
            return;
        }

        // Single-day flow (selectedDate must be set, or we could fallback to today)
        if (!selectedDate) {
            alert("먼저 날짜를 선택해주세요.");
            return;
        }

        // use startTimeInput / endTimeInput if provided else defaults
        const sTime = normalizeTimeInput(startTimeInput.value, defaultStart);
        const eTime = normalizeTimeInput(endTimeInput.value, defaultEnd);
        const payload = {
            title,
            colorTag,
            startTime: makeLocalDateTimeISO(selectedDate, sTime, defaultStart) + ":00",
            endTime: makeLocalDateTimeISO(selectedDate, eTime, defaultEnd) + ":59"
        };

        try {
            const res = await fetch('/api/schedule/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
                body: JSON.stringify(payload)
            });
            if (!res.ok) throw new Error('스케줄 생성 실패');

            inputEl.value = '';
            await calendar.refetchEvents();
            renderSchedules();
        } catch (err) {
            console.error(err);
            alert("스케줄 등록 중 오류 발생");
        }
    });

    // ------------------------------
    // 9. delete
    // ------------------------------
    window.deleteSchedule = async function(scheduleId) {
        if (!confirm("정말 삭제하시겠습니까?")) return;
        await fetch(`/api/schedule/delete/${scheduleId}`, {
            method: 'DELETE',
            headers: { [csrfHeader]: csrfToken }
        });
        await calendar.refetchEvents();
        renderSchedules();
    };

    // ------------------------------
    // 10. edit (simple title change existing behavior)
    // ------------------------------
    window.editSchedule = async function(scheduleId) {
        const newTitle = prompt("일정 제목을 수정하세요:");
        if (!newTitle) return;
        // For simplicity keep same start/end as before (could fetch existing and allow time edit)
        const scheduleObj = { title: newTitle };
        await fetch(`/api/schedule/update/${scheduleId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', [csrfHeader]: csrfToken },
            body: JSON.stringify(scheduleObj)
        });
        await calendar.refetchEvents();
        renderSchedules();
    };

    // ------------------------------
    // 11. search
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
    // 12. color select
    // ------------------------------
    document.querySelectorAll('.color-option').forEach(option => {
        option.addEventListener('click', () => {
            document.querySelectorAll('.color-option').forEach(o => o.classList.remove('selected'));
            option.classList.add('selected');
            selectedColor = option.getAttribute('data-color');
        });
    });

    // ------------------------------
    // 13. flatpickr NOT for time inputs (we used native type=time)
    //    But need to init multi UI toggles for applyToAllDays
    // ------------------------------
    if (applyToAllDaysInput) {
        applyToAllDaysInput.addEventListener('change', () => {
            const multiTime = document.getElementById('multiTimeInputs');
            const rangeTime = document.getElementById('rangeTimeInputs');
            if (applyToAllDaysInput.checked) {
                multiTime.style.display = 'block';
                rangeTime.style.display = 'none';
            } else {
                multiTime.style.display = 'none';
                rangeTime.style.display = 'block';
            }
        });
    }

}); // end DOMContentLoaded
