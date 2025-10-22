// /Noteflow/src/main/resources/static/js/schedule-edit.js

import { fetchWithCsrf, alertSuccess, alertError, formatDate, formatTime, fetchWithCsrfAndFiles } from './schedule-utils.js';
import { openMapModal } from './schedule-map.js';
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

// ------------------------------ DOM 참조 ------------------------------
const editModal = document.getElementById('editModal');
const editScheduleId = document.getElementById('editScheduleId'); // Hidden ID
const editTitle = document.getElementById('editTitle');
const editDesc = document.getElementById('editDesc');
const editStartDate = document.getElementById('editStartDate');
const editEndDate = document.getElementById('editEndDate');
const editStartTime = document.getElementById('editStartTime');
const editEndTime = document.getElementById('editEndTime');
const editAllDay = document.getElementById('editAllDay');
const editColor = document.getElementById('editColor');
const editNotify = document.getElementById('editNotify');
const editEmoji = document.getElementById('editEmoji');
const editSave = document.getElementById('editSave');
const editCancel = document.getElementById('editCancel');
const editDelete = document.getElementById('editDelete');
const editQuickAddCard = document.querySelector('#editModal .quick-add-card');
const editToggleAdvanced = document.getElementById('editToggleAdvanced');
const editAdvancedOptions = document.getElementById('editAdvancedOptions');

// 추가 옵션 필드
const editAlertType = document.getElementById('editAlertType');
const editCustomAlertValue = document.getElementById('editCustomAlertValue');
const editLocation = document.getElementById('editLocation');
const editHighlightType = document.getElementById('editHighlightType');
const editCategory = document.getElementById('editCategory');
const editCategoryTags = document.getElementById('editCategoryTags');
let editCategoryValues = []; // ['java','python',...]
const editAttachmentPath = document.getElementById('editAttachmentPath');
const editAttachmentList = document.getElementById('editAttachmentList');
const editFileUploader = document.getElementById('editFileUploader');
const editAttachmentListSlot = document.getElementById('editAttachmentListSlot');
// ✅ [추가] 알림 커스텀 필드 컨테이너
const editCustomAlertContainer = document.getElementById('editCustomAlertContainer');

// ------------------------------ 1. 유틸리티 ------------------------------

function renderEditCategoryTags() {
	if (!editCategoryTags) return;
	editCategoryTags.innerHTML = '';
	editCategoryValues.forEach((v, idx) => {
		const tag = document.createElement('span');
		tag.className = 'category-tag active';
		tag.style.display = 'inline-flex';
		tag.style.alignItems = 'center';
		tag.style.gap = '6px';
		tag.textContent = v;
		const x = document.createElement('button');
		x.type = 'button';
		x.textContent = 'X';
		x.className = 'btn small';
		x.style.marginLeft = '6px';
		x.onclick = () => {
			editCategoryValues.splice(idx, 1);
			renderEditCategoryTags();
		};
		tag.appendChild(x);
		editCategoryTags.appendChild(tag);
	});
	// 최종 전송 문자열 반영(콤마 구분)
	if (editCategory) editCategory.value = editCategoryValues.join(',');
}
// 사용자가 editCategory input에 Enter 눌러 추가할 수 있게
function tryAddEditCategory() {
	const v = (editCategory?.value || '').trim();
	if (!v) return;
	if (!editCategoryValues.includes(v)) editCategoryValues.push(v);
	editCategory.value = '';
	renderEditCategoryTags();
}
// 시간 입력 필드 토글
function toggleTimeInputs(isAllDay) {
	const timeRows = editModal.querySelector('.time-rows');
	if (!timeRows) return;
	timeRows.style.display = isAllDay ? 'none' : 'flex';
}

// ✅ [추가] 알림 사용자 정의 필드 토글 로직 함수
function toggleEditCustomAlertFields() {
	if (!editNotify || !editCustomAlertContainer) return;

	if (editNotify.value === 'custom') {
		editCustomAlertContainer.style.display = 'flex';
	} else {
		editCustomAlertContainer.style.display = 'none';
	}
}


// 편집 저장용 데이터 수집 함수 (schedule-quick-add.js의 collectData와 유사)
function collectEditData() {
	if (!editTitle) return null;

	const isAllDay = editAllDay.checked;

	// 1. 알림 분 값 가져오기
	// ✅ 'custom' 옵션 처리
	const notifyMinutesBefore = editNotify.value === 'custom' ? null : parseInt(editNotify.value, 10);

	// 2. alarmTime 계산 로직
	const startDateTimeString = editStartDate.value + 'T' + (isAllDay ? '00:00:00' : editStartTime.value + ':00');
	let alarmTimeString = null;

	if (notifyMinutesBefore !== null && notifyMinutesBefore >= 0 && startDateTimeString) {
		const startDateTime = new Date(startDateTimeString);

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
	document.querySelectorAll('input[name="editAlertType"]:checked').forEach(cb => {
		if (!cb.disabled) {
			selectedAlertTypes.push(cb.value);
		}
	});
	const alertTypeValue = selectedAlertTypes.length > 0 ? selectedAlertTypes.join(',') : '0';
	// highlightType ENUM 값 처리: 빈 값이면 'none' 사용
	const highlightValue = editHighlightType ? editHighlightType.value.trim() : null;
	const safeHighlightValue = (highlightValue === "" || highlightValue === null)
		? 'none'
		: highlightValue;

	const payload = {
		// [필수 및 기본 필드]
		// scheduleId는 URL로 전송, Payload에는 불필요
		title: editTitle.value.trim(),
		description: editDesc.value.trim(),
		colorTag: editColor.value,
		isAllDay: isAllDay,

		// [시간/날짜 필드]
		// FullCalendar에 호환되는 T 포맷 문자열로 전송
		startTime: editStartDate.value + 'T' + (isAllDay ? '00:00:00' : editStartTime.value + ':00'),
		endTime: editEndDate.value + 'T' + (isAllDay ? '23:59:59' : editEndTime.value + ':00'),

		// [알림 시간]
		alarmTime: alarmTimeString,

		// [추가 옵션 필드]
		emoji: editEmoji ? editEmoji.value.trim() : null,
		alertType: alertTypeValue,
		customAlertValue: editCustomAlertValue ? (editCustomAlertValue.value || null) : null,
		location: editLocation ? editLocation.value.trim() : null,
		mapLat: null, // 현재 UI에는 없으므로 null
		mapLng: null, // 현재 UI에는 없으므로 null
		highlightType: safeHighlightValue,
		category: editCategory ? (editCategory.value.trim() || editCategoryValues.join(',')) : null,
		attachmentPath: editAttachmentPath ? editAttachmentPath.value.trim() : null,
		attachmentList: editAttachmentList ? (editAttachmentList.value.trim() || '[]')
			: '[]',
	};

	return payload;
}


// ------------------------------ 2. Modal/UI 함수 ------------------------------

// 모달 열기 및 데이터 로드
export async function openEditModal(scheduleId) {
	if (!editModal || !scheduleId) return;

	try {
		// 1. 일정 단건 조회 API 호출
		const schedule = await fetchWithCsrf(`/api/schedule/${scheduleId}`);
		if (!schedule) throw new Error('일정 정보를 불러올 수 없습니다.');

		// 2. 데이터 바인딩
		editScheduleId.value = schedule.scheduleId;
		editTitle.value = schedule.title || '';
		editDesc.value = schedule.description || '';
		editColor.value = schedule.colorTag || '#3788d8';
		editAllDay.checked = !!schedule.isAllDay;
		editEmoji.value = schedule.emoji || '';

		// 시간/날짜 파싱
		const startTime = schedule.startTime ? new Date(schedule.startTime) : null;
		const endTime = schedule.endTime ? new Date(schedule.endTime) : null;

		if (startTime) {
			editStartDate.value = formatDate(startTime);
			editStartTime.value = formatTime(startTime);
		}
		if (endTime) {
			editEndDate.value = formatDate(endTime);
			// 하루 종일 일정은 종료 시간을 23:59:59로 보내므로 시간 파싱을 건너뜁니다.
			if (!editAllDay.checked) {
				editEndTime.value = formatTime(endTime);
			}
		}

		// 알림 시간은 복잡하므로 단순 기본값 설정 (개선 필요 영역)
		// ✅ 알림 설정 로직 개선 필요: 실제 값에 따라 editNotify.value를 설정해야 함
		editNotify.value = '-1'; // 임시로 알림 없음으로 설정

		// 추가 옵션 바인딩
		// ✅ [수정] 알림 타입 체크박스 설정
		const alertTypes = (schedule.alertType && schedule.alertType !== '0')
			? schedule.alertType.split(',')
			: [];
		document.querySelectorAll('input[name="editAlertType"]').forEach(cb => {
			cb.checked = alertTypes.includes(cb.value);
		});
		editCustomAlertValue.value = schedule.customAlertValue || '';
		editLocation.value = schedule.location || '';
		editHighlightType.value = schedule.highlightType || '';
		editCategory.value = schedule.category || '';
		// 카테고리 칩 초기화
		editCategoryValues = (schedule.category || '')
			.split(',')
			.map(v => v.trim())
			.filter(Boolean);
		renderEditCategoryTags();
		editAttachmentPath.value = schedule.attachmentPath || '';
		editAttachmentList.value = schedule.attachmentList || '';
		// ✅ [추가] 파일 목록 UI 렌더링
		editAttachmentListSlot.innerHTML = ''; // 초기화
		try {
			const files = JSON.parse(schedule.attachmentList || '[]');
			if (files && files.length > 0) {
				files.forEach(file => {
					addFileToSlot(file.fileName, file.filePath, editAttachmentListSlot);
				});
			}
		} catch (e) {
			console.error('Attachment list JSON 파싱 오류:', e);
			// (파싱 실패 시 비워둠)
		}
		// 3. UI 조정
		toggleTimeInputs(editAllDay.checked);
		if (editAdvancedOptions) editAdvancedOptions.classList.add('hidden'); // 항상 숨긴 상태로 시작
		toggleEditCustomAlertFields(); // ✅ 알림 커스텀 필드 초기 상태 설정

		// 4. 모달 표시
		editModal.classList.remove('hidden');
		editModal.setAttribute('aria-hidden', 'false');

		editModal.style.opacity = 0;
		editModal.style.transform = 'translateY(-20px) translateX(-50%)';
		requestAnimationFrame(() => {
			editModal.style.transition = 'all 0.25s ease-out';
			editModal.style.opacity = 1;
			editModal.style.transform = 'translateY(0) translateX(-50%)';
		});

	} catch (err) {
		console.error('일정 로드 실패:', err);
		alertError('일정 정보를 불러오는 데 실패했습니다.');
	}
}

// 모달 닫기
export function closeEditModal() {
	if (!editModal) return;

	editModal.style.opacity = 0;
	editModal.style.transform = 'translateY(-20px) translateX(-50%)';

	editModal.addEventListener('transitionend', function handler(e) {
		if (e.propertyName === 'opacity') {
			editModal.classList.add('hidden');
			editModal.setAttribute('aria-hidden', 'true');
			editModal.removeEventListener('transitionend', handler);
		}
	}, { once: true });
};


// ------------------------------ 3. 이벤트 핸들러 ------------------------------
// ... (handleEditSave, handleEditDelete 함수는 변경 없음) ...

function handleEditSave(e) {
	const scheduleId = editScheduleId.value;
	const payload = collectEditData();

	if (!payload.title) {
		payload.title = "(제목 없음)";
	}

	// 🚨 [핵심] 일정 수정 API 호출
	fetchWithCsrf(`/api/schedule/update/${scheduleId}`, {
		method: 'PUT',
		body: JSON.stringify(payload)
	}).then(res => {
		alertSuccess('일정이 성공적으로 수정되었습니다.');
		if (window.refreshEvents) window.refreshEvents(); // 캘린더 갱신
		closeEditModal();
	}).catch(err => {
		console.error(err);
		alertError(`일정 수정 중 오류가 발생했습니다. (${err.message || '알 수 없는 오류'})`);
	});
}

function handleEditDelete(e) {
	const scheduleId = editScheduleId.value;
	// ✅ alert() 대신 커스텀 모달 사용 필요하지만, 현재는 confirm 유지
	if (!confirm('정말로 이 일정을 삭제하시겠습니까?')) return;

	// 🚨 [핵심] 일정 삭제 API 호출
	fetchWithCsrf(`/api/schedule/delete/${scheduleId}`, {
		method: 'DELETE'
	}).then(res => {
		alertSuccess('일정이 성공적으로 삭제되었습니다.');
		if (window.refreshEvents) window.refreshEvents(); // 캘린더 갱신
		closeEditModal();
	}).catch(err => {
		console.error(err);
		alertError(`일정 삭제 중 오류가 발생했습니다. (${err.message || '알 수 없는 오류'})`);
	});
}


// ------------------------------ 4. 이벤트 리스너 등록 ------------------------------

document.addEventListener('DOMContentLoaded', () => {
	if (editModal) editModal.classList.add('hidden');

	if (editAllDay) {
		editAllDay.addEventListener('change', (e) => {
			toggleTimeInputs(e.target.checked);
		});
	}

	// ✅ [추가] editNotify 드롭다운 변경 시 커스텀 알림 필드 토글
	if (editNotify) {
		editNotify.addEventListener('change', toggleEditCustomAlertFields);
	}

	// 추가 옵션 토글 이벤트
	if (editToggleAdvanced && editAdvancedOptions) {
		editToggleAdvanced.addEventListener('click', () => {
			const isHidden = editAdvancedOptions.classList.toggle('hidden');
			editToggleAdvanced.textContent = isHidden ? '추가 옵션 보기' : '추가 옵션 닫기';
		});
	}

	if (editQuickAddCard) {
		editQuickAddCard.addEventListener('click', (e) => {
			e.stopPropagation();
		});
	}
	// ✅ [추가] 파일 업로드 및 삭제 이벤트 리스너 연결
	if (editFileUploader) {
		editFileUploader.addEventListener('change', (e) => {
			handleFileUpload(e, editAttachmentListSlot, editAttachmentPath, editAttachmentList);
		});
	}
	if (editAttachmentListSlot) {
		editAttachmentListSlot.addEventListener('click', (e) => {
			handleFileDelete(e, editAttachmentListSlot, editAttachmentPath, editAttachmentList);
		});
	}
	// ✅ [추가] 지도 버튼 이벤트 리스너
	const editMapBtn = document.getElementById('editMapBtn');
	if (editMapBtn) {
		editMapBtn.addEventListener('click', () => {
			openMapModal(editLocation); // editLocation Input 요소를 타겟으로 전달
		});
	}
	// 카테고리 Enter 입력 → 칩 추가
	if (editCategory) {
		editCategory.addEventListener('keydown', (e) => {
			if (e.key === 'Enter') {
				e.preventDefault();
				tryAddEditCategory();
			}
		});
	}
	// 버튼 이벤트 리스너 등록
	if (editSave) editSave.addEventListener('click', handleEditSave);
	if (editCancel) editCancel.addEventListener('click', closeEditModal);
	if (editDelete) editDelete.addEventListener('click', handleEditDelete);

	// ESC 키 닫기 (schedule-quick-add.js와 동일하게 구현)
	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape' && editModal && !editModal.classList.contains('hidden')) {
			closeEditModal();
		}
	});

	// 모달 외부 클릭 닫기 (schedule-quick-add.js와 동일하게 구현)
	document.addEventListener('click', (e) => {
		if (editModal && !editModal.classList.contains('hidden')) {
			const isClickOutside = !editModal.contains(e.target);
			if (isClickOutside) closeEditModal();
		}
	});

	// ✅ 초기 상태 설정
	toggleEditCustomAlertFields();
});
