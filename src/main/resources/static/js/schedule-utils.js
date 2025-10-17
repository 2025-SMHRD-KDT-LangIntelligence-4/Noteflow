export const formatDate = (date) => date ? new Date(date).toISOString().slice(0, 10) : '';
export const formatTime = (time) => time ? new Date(time).toTimeString().slice(0, 5) : '';

export const fetchWithCsrf = async (url, options = {}) => {
	const csrfToken = document.querySelector('meta[name="_csrf"]').content;
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

	const headers = { ...options.headers, [csrfHeader]: csrfToken, 'Content-Type': 'application/json' };
	const response = await fetch(url, { ...options, headers });
	if (!response.ok) throw new Error(`HTTP 오류! 상태: ${response.status}`);
	const text = await response.text();
	if (!text) return null;
	try { return JSON.parse(text); } catch (e) { return text; }
};

export const alertSuccess = (msg) => Swal.fire({ icon: 'success', text: msg, timer: 1500, showConfirmButton: false });
export const alertError = (msg) => Swal.fire({ icon: 'error', text: msg, timer: 2000, showConfirmButton: false });
