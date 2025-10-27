// /Noteflow/src/main/resources/static/js/schedule-edit.js

import { fetchWithCsrf, alertSuccess, alertError, formatDate, formatTime, fetchWithCsrfAndFiles } from './schedule-utils.js';
import { openMapModal } from './schedule-map.js';

// ------------------------------ 파일 업로드/첨부 관련 유틸 ------------------------------
async function handleFileUpload(e, slotElement, pathInput, listInput) {
	const files = e.target.files;
	if (!files.length) return;

	for (const file of files) {
		const formData = new FormData();
		formData.append('file', file);

		try {
			const result = await fetchWithCsrfAndFiles('/api/schedule-files/upload', formData);
			addFileToSlot(result.fileName, result.filePath, slotElement);
		} catch (err) {
			alertError(`'${file.name}' 업로드 실패: ` + err.message);
		}
	}

	updateHiddenAttachmentInputs(slotElement, pathInput, listInput);
	e.target.value = null; // 같은 파일 다시 선택 가능하도록 초기화
}

function handleFileDelete(e, slotElement, pathInput, listInput) {
	if (e.target.classList.contains('file-delete-btn')) {
		const fileItem = e.target.closest('.file-item');

		// TODO: 서버 실제 삭제 API 호출 자리 (필요 시)
		fileItem.remove();

		updateHiddenAttachmentInputs(slotElement, pathInput, listInput);
		alertSuccess('파일 목록에서 제거되었습니다. (서버 삭제 처리 필요)');
	}
}

function addFileToSlot(fileName, filePath, slotElement) {
	const fileItem = document.createElement('div');
	fileItem.className = 'file-item';
	fileItem.dataset.path = filePath;
	fileItem.innerHTML = `${fileName} <span class="file-delete-btn" title="목록에서 제거">X</span>`;
	slotElement.appendChild(fileItem);
}

function updateHiddenAttachmentInputs(slotElement, pathInput, listInput) {
	const items = [];
	slotElement.querySelectorAll('.file-item').forEach(item => {
		items.push({
			fileName: item.textContent.replace(/ X$/, ''), // "X" 제거
			filePath: item.dataset.path
		});
	});

	pathInput.value = items.length > 0 ? items[0].filePath : '';
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
const editEmoji = document.getElementById('editEmoji');

const editNotify = document.getElementById('editNotify');
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

// 알림 커스텀 필드 컨테이너
const editCustomAlertContainer = document.getElementById('editCustomAlertContainer');

// ------------------------------ 유틸 로직 ------------------------------
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

	// 콤마 문자열로 editCategory input에 반영
	if (editCategory) editCategory.value = editCategoryValues.join(',');
}

// Enter로 카테고리 추가
function tryAddEditCategory() {
	const v = (editCategory?.value || '').trim();
	if (!v) return;
	if (!editCategoryValues.includes(v)) editCategoryValues.push(v);
	editCategory.value = '';
	renderEditCategoryTags();
}

// 하루종일 toggle → 시간 필드 show/hide
function toggleTimeInputs(isAllDay) {
	const timeRows = editModal.querySelector('.time-rows');
	if (!timeRows) return;
	timeRows.style.display = isAllDay ? 'none' : 'flex';
}

// 알림 "직접 설정..." 선택 시 커스텀 필드 보이기/숨기기
function toggleEditCustomAlertFields() {
	if (!editNotify || !editCustomAlertContainer) return;

	if (editNotify.value === 'custom') {
		editCustomAlertContainer.style.display = 'flex';
	} else {
		editCustomAlertContainer.style.display = 'none';
	}
}

// 편집 저장 시 서버에 보낼 payload 구성
function collectEditData() {
	if (!editTitle) return null;

	const isAllDay = editAllDay.checked;

	// 알림 분 값
	const notifyMinutesBefore =
		editNotify.value === 'custom' ? null : parseInt(editNotify.value, 10);

	// alarmTime 계산
	const startDateTimeString =
		editStartDate.value + 'T' + (isAllDay ? '00:00:00' : editStartTime.value + ':00');

	let alarmTimeString = null;
	if (notifyMinutesBefore !== null && notifyMinutesBefore >= 0 && startDateTimeString) {
		const startDateTime = new Date(startDateTimeString);

		if (notifyMinutesBefore > 0) {
			startDateTime.setMinutes(startDateTime.getMinutes() - notifyMinutesBefore);
		}

		const year = startDateTime.getFullYear();
		const month = String(startDateTime.getMonth() + 1).padStart(2, '0');
		const day = String(startDateTime.getDate()).padStart(2, '0');
		const hours = String(startDateTime.getHours()).padStart(2, '0');
		const minutes = String(startDateTime.getMinutes()).padStart(2, '0');
		const seconds = String(startDateTime.getSeconds()).padStart(2, '0');

		alarmTimeString = `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
	}

	// 알림 타입 다중 선택 수집
	const selectedAlertTypes = [];
	document.querySelectorAll('input[name="editAlertType"]:checked').forEach(cb => {
		if (!cb.disabled) {
			selectedAlertTypes.push(cb.value);
		}
	});
	const alertTypeValue = selectedAlertTypes.length > 0 ? selectedAlertTypes.join(',') : '0';

	// highlightType ENUM 보정
	const highlightValue = editHighlightType ? editHighlightType.value.trim() : null;
	const safeHighlightValue =
		(highlightValue === '' || highlightValue === null)
			? 'none'
			: highlightValue;

	const payload = {
		title: editTitle.value.trim(),
		description: editDesc.value.trim(),

		// ✅ 컬러 (outline 포함)
		colorTag: editColor.value,

		isAllDay: isAllDay,

		startTime:
			editStartDate.value + 'T' + (isAllDay ? '00:00:00' : editStartTime.value + ':00'),
		endTime:
			editEndDate.value + 'T' + (isAllDay ? '23:59:59' : editEndTime.value + ':00'),

		alarmTime: alarmTimeString,

		emoji: editEmoji ? editEmoji.value.trim() : null,
		alertType: alertTypeValue,
		customAlertValue: editCustomAlertValue ? (editCustomAlertValue.value || null) : null,
		location: editLocation ? editLocation.value.trim() : null,
		mapLat: null,
		mapLng: null,
		highlightType: safeHighlightValue,

		category: editCategory
			? (editCategory.value.trim() || editCategoryValues.join(','))
			: null,

		attachmentPath: editAttachmentPath ? editAttachmentPath.value.trim() : null,
		attachmentList: editAttachmentList
			? (editAttachmentList.value.trim() || '[]')
			: '[]',
	};

	return payload;
}

// ------------------------------ 모달 오픈 / 클로즈 ------------------------------
export async function openEditModal(scheduleId) {
	if (!editModal || !scheduleId) return;

	try {
		// 일정 단건 조회
		const schedule = await fetchWithCsrf(`/api/schedule/${scheduleId}`);
		if (!schedule) throw new Error('일정 정보를 불러올 수 없습니다.');

		// snake/camel 혼용 대비
		const apiScheduleId = schedule.scheduleId ?? schedule.schedule_id ?? scheduleId;
		const colorTagValue = schedule.colorTag ?? schedule.color_tag ?? null;
		const isAllDayValue = schedule.isAllDay ?? schedule.is_all_day ?? false;
		const startTimeRaw = schedule.startTime ?? schedule.start_time ?? null;
		const endTimeRaw = schedule.endTime ?? schedule.end_time ?? null;

		// 값 채우기
		editScheduleId.value = apiScheduleId;
		editTitle.value = schedule.title || '';
		editDesc.value = schedule.description || '';

		// ✅ colorTag: outline 기본 사용
		editColor.value = colorTagValue || 'outline';

		editAllDay.checked = !!isAllDayValue;
		editEmoji.value = schedule.emoji || '';

		// 날짜/시간
		const startTime = startTimeRaw ? new Date(startTimeRaw) : null;
		const endTime = endTimeRaw ? new Date(endTimeRaw) : null;

		if (startTime) {
			editStartDate.value = formatDate(startTime);
			editStartTime.value = formatTime(startTime);
		}
		if (endTime) {
			editEndDate.value = formatDate(endTime);
			if (!editAllDay.checked) {
				editEndTime.value = formatTime(endTime);
			}
		}

		// 알림 드롭다운 초기값
		// TODO: 실제 alarmTime / 알림 설정 기반으로 값을 복원하려면 여기 로직 확장
		editNotify.value = '-1';

		// 알림 타입 체크박스
		const alertTypesRaw = schedule.alertType ?? schedule.alert_type ?? '0';
		const alertTypes = (alertTypesRaw && alertTypesRaw !== '0')
			? alertTypesRaw.split(',')
			: [];
		document.querySelectorAll('input[name="editAlertType"]').forEach(cb => {
			cb.checked = alertTypes.includes(cb.value);
		});

		editCustomAlertValue.value = schedule.customAlertValue ?? schedule.custom_alert_value ?? '';
		editLocation.value = schedule.location || '';
		editHighlightType.value = schedule.highlightType ?? schedule.highlight_type ?? '';

		// 카테고리
		const rawCategory = schedule.category || '';
		editCategory.value = rawCategory;
		editCategoryValues = rawCategory
			.split(',')
			.map(v => v.trim())
			.filter(Boolean);
		renderEditCategoryTags();

		// 첨부파일
		editAttachmentPath.value = schedule.attachmentPath ?? schedule.attachment_path ?? '';
		editAttachmentList.value = schedule.attachmentList ?? schedule.attachment_list ?? '';
		editAttachmentListSlot.innerHTML = '';
		try {
			const files = JSON.parse(editAttachmentList.value || '[]');
			if (files && files.length > 0) {
				files.forEach(file => {
					addFileToSlot(file.fileName, file.filePath, editAttachmentListSlot);
				});
			}
		} catch (e) {
			console.error('Attachment list JSON 파싱 오류:', e);
		}

		// UI 상태 조정
		toggleTimeInputs(editAllDay.checked);
		if (editAdvancedOptions) editAdvancedOptions.classList.add('hidden');
		toggleEditCustomAlertFields();

		// 모달 표시 애니메이션
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
}

// ------------------------------ 저장 / 삭제 핸들러 ------------------------------
function handleEditSave() {
	const scheduleIdVal = editScheduleId.value;
	const payload = collectEditData();

	if (!payload.title) {
		payload.title = "(제목 없음)";
	}

	fetchWithCsrf(`/api/schedule/update/${scheduleIdVal}`, {
		method: 'PUT',
		body: JSON.stringify(payload)
	}).then(res => {
		alertSuccess('일정이 성공적으로 수정되었습니다.');
		if (window.refreshEvents) window.refreshEvents();
		closeEditModal();
	}).catch(err => {
		console.error(err);
		alertError(`일정 수정 중 오류가 발생했습니다. (${err.message || '알 수 없는 오류'})`);
	});
}

function handleEditDelete() {
	const scheduleIdVal = editScheduleId.value;
	if (!confirm('정말로 이 일정을 삭제하시겠습니까?')) return;

	fetchWithCsrf(`/api/schedule/delete/${scheduleIdVal}`, {
		method: 'DELETE'
	}).then(res => {
		alertSuccess('일정이 성공적으로 삭제되었습니다.');
		if (window.refreshEvents) window.refreshEvents();
		closeEditModal();
	}).catch(err => {
		console.error(err);
		alertError(`일정 삭제 중 오류가 발생했습니다. (${err.message || '알 수 없는 오류'})`);
	});
}

// ------------------------------ 이벤트 리스너 등록 ------------------------------
document.addEventListener('DOMContentLoaded', () => {
	if (editModal) editModal.classList.add('hidden');

	if (editAllDay) {
		editAllDay.addEventListener('change', (e) => {
			toggleTimeInputs(e.target.checked);
		});
	}

	if (editNotify) {
		editNotify.addEventListener('change', toggleEditCustomAlertFields);
	}

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

	const editMapBtn = document.getElementById('editMapBtn');
	if (editMapBtn) {
		editMapBtn.addEventListener('click', () => {
			openMapModal(editLocation);
		});
	}

	if (editCategory) {
		editCategory.addEventListener('keydown', (e) => {
			if (e.key === 'Enter') {
				e.preventDefault();
				tryAddEditCategory();
			}
		});
	}

	if (editSave) editSave.addEventListener('click', handleEditSave);
	if (editCancel) editCancel.addEventListener('click', closeEditModal);
	if (editDelete) editDelete.addEventListener('click', handleEditDelete);

	// ESC 눌러 닫기
	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape' && editModal && !editModal.classList.contains('hidden')) {
			closeEditModal();
		}
	});

	// 모달 밖 클릭하면 닫기
	document.addEventListener('click', (e) => {
		if (!editModal || editModal.classList.contains('hidden')) return;

		// 지도 모달 열려있으면 무시
		if (window.__MAP_MODAL_OPEN) return;

		// 지도 모달 내부 클릭 무시
		const inMapModal = e.target.closest && e.target.closest('#kakaoMapModal');
		if (inMapModal) return;

		const isClickOutside = !editModal.contains(e.target);
		if (isClickOutside) closeEditModal();
	});

	// 초기 알림 커스텀 필드 표시 상태 동기화
	toggleEditCustomAlertFields();
});
