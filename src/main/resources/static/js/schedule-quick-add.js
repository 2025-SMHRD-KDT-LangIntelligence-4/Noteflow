// 시간 입력 필드 토글을 위한 컨테이너 참조
const timeRows = document.querySelector('.time-rows');
function toggleTimeInputs(isAllDay) {
    const timeRows = document.querySelector('.time-rows');
    if (!timeRows) return;
    timeRows.style.display = isAllDay ? 'none' : 'flex';
}


// ------------------------------ + 버튼 주입 ------------------------------
window.injectPlusButtons = function injectPlusButtons() {
	document.querySelectorAll('.fc-daygrid-day').forEach(dayCell => {
		// dayCell의 상단 컨테이너 (날짜 숫자를 포함하는)
		const dayTop = dayCell.querySelector('.fc-daygrid-day-top');

		// 이미 버튼이 있거나, 상단 컨테이너가 없으면 건너뜀
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

		// + 버튼을 .fc-daygrid-day-top 내부에 주입
		dayTop.appendChild(btn);

		// dayCell.style.position = 'static'; (CSS에서 처리하므로 주석 처리)
	});
};


document.addEventListener('DOMContentLoaded', () => {



    // ------------------------------ 모달 관련 ------------------------------
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

// 1. 초기 화면에서 모달 숨김
	if (quickModal) quickModal.classList.add('hidden');

		// 2. 하루종일 체크박스 토글 이벤트 추가
		if (qaAllDay) {
			qaAllDay.addEventListener('change', (e) => {
				toggleTimeInputs(e.target.checked);
			});
		}
		window.openQuickAddModal = function openQuickAddModal(dateStr) {
				qaTitle.value = '';
				qaDesc.value = '';
				qaColor.value = '#3788d8';
				qaAllDay.checked = false;
				qaNotify.value = '0';

				const today = dateStr ? new Date(dateStr) : new Date();
				const dateISO = today.toISOString().slice(0, 10);

				qaStartDate.value = dateStr || dateISO;
				qaEndDate.value = dateStr || dateISO;
				qaStartTime.value = '09:00';
				qaEndTime.value = '10:00';
				
				// 모달 열 때 시간 입력 필드 초기 상태 설정
				toggleTimeInputs(qaAllDay.checked);

				quickModal.classList.remove('hidden');
				quickModal.setAttribute('aria-hidden', 'false');
				quickModal.style.opacity = 0;
				quickModal.style.transform = 'translateY(-20px)';
				requestAnimationFrame(() => {
					quickModal.style.transition = 'all 0.25s ease-out';
					quickModal.style.opacity = 1;
					quickModal.style.transform = 'translateY(0)';
				});
			};

			window.closeQuickAddModal = function closeQuickAddModal() {
				quickModal.style.opacity = 0;
				quickModal.style.transform = 'translateY(-20px)';
				setTimeout(() => {
					quickModal.classList.add('hidden');
					quickModal.setAttribute('aria-hidden', 'true');
				}, 250);
			};


			// ------------------------------ 저장 로직 ------------------------------
				qaSave.addEventListener('click', async () => {
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
						const csrfToken = document.querySelector('meta[name="_csrf"]').content;
						const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

						const res = await fetch('/api/schedule/create', {
							method: 'POST',
							headers: {
								'Content-Type': 'application/json',
								[csrfHeader]: csrfToken
							},
							body: JSON.stringify(payload)
						});

						if (!res.ok) throw new Error('생성 실패');

						if (window.refreshEvents && typeof window.refreshEvents === 'function') {
							await window.refreshEvents();
						} else if (window.calendar && typeof window.calendar.refetchEvents === 'function') {
							await window.calendar.refetchEvents();
						}

						closeQuickAddModal();
					} catch (err) {
						console.error(err);
						alert('일정 생성 중 오류가 발생했습니다.');
					}
				});

				qaCancel.addEventListener('click', closeQuickAddModal);
				quickModal.addEventListener('click', (e) => {
					if (e.target === quickModal) closeQuickAddModal();
				});

				// ------------------------------ FullCalendar 렌더 감시 (제거) ------------------------------
				/* * schedule-manager.js의 datesSet 콜백에서 injectPlusButtons를 호출하므로, 
				 * 기존의 setInterval 및 MutationObserver 로직은 제거하고, 초기 로딩 시에만 호출
				 */
				if (document.querySelectorAll('.fc-daygrid-day').length === 0) {
					const interval = setInterval(() => {
						const dayCells = document.querySelectorAll('.fc-daygrid-day');
						if (dayCells.length > 0) {
							injectPlusButtons();
							clearInterval(interval);
						}
					}, 300);
				}
			}); // <--- **DOMContentLoaded 닫는 괄호**