// schedule-ui-dropdown.js
// 드롭다운 및 기간 선택 UI 제어 모듈

export const initDropdowns = () => {
  const periodCheckbox = document.querySelector('#periodCheck');
  const startDateInput = document.querySelector('#startDate');
  const endDateInput = document.querySelector('#endDate');
  const startTimeInput = document.querySelector('#startTime');
  const endTimeInput = document.querySelector('#endTime');

  if (!periodCheckbox) return;

  // "기간 동일 적용" 체크 시 날짜/시간 동기화
  periodCheckbox.addEventListener('change', () => {
    if (periodCheckbox.checked) {
      endDateInput.value = startDateInput.value;
      endTimeInput.value = startTimeInput.value;
      startDateInput.disabled = false;
      endDateInput.disabled = true;
      endTimeInput.disabled = true;
    } else {
      endDateInput.disabled = false;
      endTimeInput.disabled = false;
    }
  });

  // 시작일 변경 시 자동 동기화
  startDateInput.addEventListener('change', () => {
    if (periodCheckbox.checked) endDateInput.value = startDateInput.value;
  });

  // 시작시간 변경 시 자동 동기화
  startTimeInput.addEventListener('change', () => {
    if (periodCheckbox.checked) endTimeInput.value = startTimeInput.value;
  });
};

// 색상 선택 Dropdown 초기화
export const initColorDropdown = () => {
  const colorOptions = document.querySelectorAll('.color-option');
  const colorPreview = document.querySelector('#colorPreview');

  colorOptions.forEach((opt) => {
    opt.addEventListener('click', () => {
      const selectedColor = opt.dataset.color;
      colorPreview.style.backgroundColor = selectedColor;
      colorPreview.dataset.color = selectedColor;
    });
  });
};
