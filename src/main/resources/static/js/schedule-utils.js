// schedule-utils.js
// 공통 유틸리티 모듈

// 날짜 포맷 변환
export const formatDate = (date) => {
  if (!date) return '';
  const d = new Date(date);
  return d.toISOString().slice(0, 10);
};

// 시간 포맷 변환
export const formatTime = (time) => {
  if (!time) return '';
  const d = new Date(time);
  return d.toTimeString().slice(0, 5);
};

// AJAX 요청 (CSRF 포함)
export const fetchWithCsrf = async (url, options = {}) => {
  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  const headers = {
    ...options.headers,
    [csrfHeader]: csrfToken,
    'Content-Type': 'application/json',
  };

  const response = await fetch(url, { ...options, headers });
  if (!response.ok) throw new Error(`HTTP 오류! 상태: ${response.status}`);
  return response.json();
};

// SweetAlert 단축 함수
export const alertSuccess = (msg) =>
  Swal.fire({ icon: 'success', text: msg, timer: 1500, showConfirmButton: false });

export const alertError = (msg) =>
  Swal.fire({ icon: 'error', text: msg, timer: 2000, showConfirmButton: false });
