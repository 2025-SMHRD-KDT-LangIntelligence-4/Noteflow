// 전역 변수
const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

let currentTab = 'notes'; // 'notes' | 'files'
let selectedItem = null;
let selectedItemType = null; // 'note' | 'file' | 'noteFolder' | 'folder'
let dragging = false;

const itemsData = {
	notes: [],
	noteFolders: [],
	files: [],
	fileFolders: [],
	folders: [],
	rootNotes: [],
	rootFiles: []
};

// CSRF를 secureFetch에서만 세팅 (jQuery ajaxSend 제거)
window.secureFetch = async function (url, options = {}) {
	const headers = options.headers instanceof Headers ? options.headers : new Headers(options.headers || {});
	if (csrfToken) headers.set(csrfHeader, csrfToken);
	options.headers = headers;
	return fetch(url, options);
};

// 초기화
document.addEventListener('DOMContentLoaded', function () {
	setupEventListeners();
	loadData();
});

function setupEventListeners() {
	// 탭 클릭
	document.querySelectorAll('.tab-button').forEach(btn => {
		btn.addEventListener('click', function () {
			switchTab(this.dataset.tab);
		});
	});
	// 검색
	document.getElementById('searchInput').addEventListener('input', function () {
		filterItems(this.value);
	});
	// 파일 업로드
	document.getElementById('fileInput').addEventListener('change', function () {
		handleFileUpload(this.files);
	});
	// 컨텍스트 메뉴 숨기기
	document.addEventListener('click', function () {
		hideContextMenu();
	});
	// 드래그 전역 상태
	document.addEventListener('dragstart', () => { dragging = true; }, true);
	document.addEventListener('dragend', () => { setTimeout(() => dragging = false, 0); }, true);
}

// 탭 전환
function switchTab(tab) {
	if (currentTab === tab) return;
	currentTab = tab;
	document.querySelectorAll('.tab-button').forEach(btn => {
		btn.classList.toggle('active', btn.dataset.tab === tab);
	});
	selectedItem = null;
	selectedItemType = null;
	clearContent();
	loadData();
}

// 데이터 로드
function loadData() {
	if (currentTab === 'notes') {
		loadNotesWithFolders();
	} else {
		loadFilesWithFolders();
	}
}

async function loadNotesWithFolders() {
	try {
		const res = await secureFetch('/api/unified/notes/tree');
		if (res.ok) {
			const data = await res.json();
			itemsData.noteFolders = data.folders || [];
			itemsData.notes = data.rootNotes || [];
		} else {
			itemsData.noteFolders = [];
			itemsData.notes = [];
		}
	} catch (e) {
		console.error('노트/폴더 로드 실패:', e);
		itemsData.noteFolders = [];
		itemsData.notes = [];
	}
	renderItemList();
}

async function loadFilesWithFolders() {
	try {
		const res = await secureFetch('/api/unified/files/tree');
		if (res.ok) {
			const data = await res.json();
			itemsData.fileFolders = data.folders || [];
			itemsData.files = data.rootFiles || [];
		} else {
			itemsData.fileFolders = [];
			itemsData.files = [];
		}
	} catch (e) {
		console.error('파일/폴더 로드 실패:', e);
		itemsData.fileFolders = [];
		itemsData.files = [];
	}
	renderItemList();
}

// 목록 렌더링
function renderItemList() {
	const listContainer = document.getElementById('itemList');
	listContainer.innerHTML = '';

	if (currentTab === 'notes') {
		const noFolders = !itemsData.noteFolders || itemsData.noteFolders.length === 0;
		const noNotes = !itemsData.notes || itemsData.notes.length === 0;
		if (noFolders && noNotes) {
			listContainer.innerHTML = '<div class="welcome-message"><p>저장된 정리본이 없습니다</p></div>';
			return;
		}
		// 루트 노트
		if (itemsData.notes?.length) {
			const header = document.createElement('div');
			header.className = 'folder-header';
			header.innerHTML = '<strong>📝 루트 노트</strong>';
			listContainer.appendChild(header);
			itemsData.notes.forEach(note => {
				const el = createNoteElement(note, 0);
				listContainer.appendChild(el);
			});
		}
		// 폴더 트리
		itemsData.noteFolders?.forEach(folder => {
			renderNoteFolderTree(listContainer, folder, 0);
		});
	} else {
		const noFolders = !itemsData.fileFolders || itemsData.fileFolders.length === 0;
		const noFiles = !itemsData.files || itemsData.files.length === 0;
		if (noFolders && noFiles) {
			listContainer.innerHTML = '<div class="welcome-message"><p>업로드된 파일이 없습니다</p></div>';
			return;
		}
		// 루트 파일
		if (itemsData.files?.length) {
			const header = document.createElement('div');
			header.className = 'folder-header';
			header.innerHTML = '<strong>📁 루트 파일</strong>';
			listContainer.appendChild(header);
			itemsData.files.forEach(file => {
				const el = createFileElement(file, 0);
				listContainer.appendChild(el);
			});
		}
		// 파일 폴더 트리
		itemsData.fileFolders?.forEach(folder => {
			renderFileFolderTree(listContainer, folder, 0);
		});
	}
}

// 노트 폴더 트리 렌더링
function renderNoteFolderTree(container, folder, depth) {
	const folderEl = createNoteFolderElement(folder, depth);
	container.appendChild(folderEl);

	// 하위 노트
	if (Array.isArray(folder.notes) && folder.notes.length) {
		folder.notes.forEach(note => {
			const noteEl = createNoteElement(note, depth + 1);
			container.appendChild(noteEl);
		});
	}
	// 하위 폴더
	if (Array.isArray(folder.subfolders) && folder.subfolders.length) {
		folder.subfolders.forEach(sub => {
			renderNoteFolderTree(container, sub, depth + 1);
		});
	}
}

// 요소 생성 - 폴더(노트)
function createNoteFolderElement(folder, depth = 0) {
	const div = document.createElement('div');
	div.className = 'folder-item';
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
      <span class="folder-icon">📁</span>
      <span class="folder-name">${escapeHtml(folder.folderName || '')}</span>
      <div class="folder-actions">
        <button class="action-btn" onclick="renameNoteFolder('${folder.folderId}')">✏️</button>
        <button class="action-btn" onclick="deleteNoteFolder('${folder.folderId}')">🗑️</button>
      </div>
    `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectFolder(folder, div, 'noteFolder');
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, folder, 'noteFolder'));

	// 드래그-드롭(노트 대상)
	div.addEventListener('dragover', handleDragOver);
	div.addEventListener('drop', (e) => handleNoteDrop(e, folder.folderId));
	div.addEventListener('dragleave', handleDragLeave);
	return div;
}

// 요소 생성 - 노트
function createNoteElement(note, depth = 0) {
	const div = document.createElement('div');
	div.className = 'note-item';
	div.draggable = true;
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
      <span class="note-icon">📄</span>
      <span class="note-title">${escapeHtml(note.title || '')}</span>
    `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectNote(note, div);
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, note, 'note'));
	div.addEventListener('dragstart', (e) => handleDragStart(e, note, 'note'));
	div.addEventListener('dragend', handleDragEnd);
	return div;
}

// 요소 생성 - 파일 폴더(Mongo)
function createFolderElement(folder, depth = 0) {
	const div = document.createElement('div');
	div.className = 'folder-item';
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
      <span class="folder-icon">📁</span>
      <span class="folder-name">${escapeHtml(folder.name || '')}</span>
      <div class="folder-actions">
        <button class="action-btn" onclick="renameFolder('${folder.id}')">✏️</button>
        <button class="action-btn" onclick="deleteFolder('${folder.id}')">🗑️</button>
      </div>
    `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectFolder(folder, div, 'folder');
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, folder, 'folder'));

	// 파일 드롭용
	div.addEventListener('dragover', handleDragOver);
	div.addEventListener('drop', (e) => handleFileDrop(e, folder.id));
	div.addEventListener('dragleave', handleDragLeave);
	return div;
}

// 요소 생성 - 파일
function createFileElement(file, depth = 0) {
	const div = document.createElement('div');
	div.className = 'file-item';
	div.draggable = true;
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
      <span class="file-icon">${file.fileIcon || getFileIcon(file.originalName || '')}</span>
      <span class="file-name">${escapeHtml(file.originalName || '')}</span>
    `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectFile(file, div);
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, file, 'file'));
	div.addEventListener('dragstart', (e) => handleDragStart(e, file, 'file'));
	div.addEventListener('dragend', handleDragEnd);
	return div;
}

// 선택 상태 표시
function updateSelectedState(el) {
	document.querySelectorAll('.note-item, .file-item, .folder-item')
		.forEach(e => e.classList.remove('selected'));
	if (el) el.classList.add('selected');
}

// 선택 로직
function selectNote(note, el) {
	selectedItem = note;
	selectedItemType = 'note';
	updateSelectedState(el);
	showNoteContent(note);
}
function selectFile(file, el) {
	selectedItem = file;
	selectedItemType = 'file';
	updateSelectedState(el);
	showFileContent(file);
}
function selectFolder(folder, el, type) {
	selectedItem = folder;
	selectedItemType = type; // 'noteFolder' | 'folder'
	updateSelectedState(el);
	showFolderContent(folder);
}

// 컨텍스트 메뉴
function showContextMenu(e, item, type) {
	e.preventDefault();
	const menu = document.getElementById('contextMenu');
	menu.style.display = 'block';
	menu.style.left = e.pageX + 'px';
	menu.style.top = e.pageY + 'px';
	menu.dataset.itemId = item.id || item.noteIdx || item.folderId;
	menu.dataset.itemType = type;
}
function hideContextMenu() {
	const menu = document.getElementById('contextMenu');
	if (menu) menu.style.display = 'none';
}

// 표시 로직
async function showNoteContent(note) {
	const titleEl = document.getElementById('itemTitle');
	const contentEl = document.getElementById('itemContent');
	titleEl.textContent = note.title || '(제목 없음)';
	contentEl.value = note.content || '';
	contentEl.classList.add('readonly');
	contentEl.readOnly = true;
	updateButtons('note');
}

async function showFileContent(file) {
	const titleEl = document.getElementById('itemTitle');
	const contentEl = document.getElementById('itemContent');
	titleEl.textContent = file.originalName || '(파일)';
	contentEl.value = '불러오는 중...';
	try {
		const res = await secureFetch(`/api/files/preview/${file.id}`);
		contentEl.value = res.ok ? await res.text() : '파일 미리보기를 불러올 수 없습니다.';
	} catch {
		contentEl.value = '파일 로드 중 오류가 발생했습니다.';
	}
	contentEl.classList.add('readonly');
	contentEl.readOnly = true;
	updateButtons('file');
}

function showFolderContent(folder) {
	const titleEl = document.getElementById('itemTitle');
	const contentEl = document.getElementById('itemContent');
	titleEl.textContent = `📁 ${folder.name || folder.folderName || '(폴더)'}`;
	contentEl.value = '폴더가 선택되었습니다.';
	contentEl.classList.add('readonly');
	contentEl.readOnly = true;
	updateButtons('folder');
}

// 버튼 UI
function updateButtons(type) {
	const container = document.getElementById('buttonContainer');
	container.innerHTML = '';
	if (type === 'note') {
		container.innerHTML = `
        <button class="btn-primary" onclick="editNote()">✏️ 수정하기</button>
        <button class="btn-success hidden" id="saveBtn" onclick="saveNote()">💾 저장</button>
        <button class="btn-secondary hidden" id="cancelBtn" onclick="cancelEdit()">❌ 취소</button>
        <button class="btn-warning" onclick="downloadNote()">💾 다운로드</button>
        <button class="btn-danger" onclick="deleteNote()">🗑️ 삭제</button>
      `;
	} else if (type === 'file') {
		container.innerHTML = `
        <button class="btn-warning" onclick="downloadFile()">💾 다운로드</button>
        <button class="btn-danger" onclick="deleteFile()">🗑️ 삭제</button>
      `;
	} else if (type === 'folder') {
		container.innerHTML = `
        <button class="btn-primary" onclick="renameFolder()">✏️ 이름변경</button>
        <button class="btn-danger" onclick="deleteFolder()">🗑️ 삭제</button>
      `;
	}
}

// 노트 편집/저장
function editNote() {
	const contentEl = document.getElementById('itemContent');
	const titleEl = document.getElementById('itemTitle');
	contentEl.classList.remove('readonly');
	contentEl.readOnly = false;
	titleEl.classList.remove('readonly');
	document.querySelector('.btn-primary').classList.add('hidden');
	document.getElementById('saveBtn').classList.remove('hidden');
	document.getElementById('cancelBtn').classList.remove('hidden');
	contentEl.focus();
}
async function saveNote() {
	if (!selectedItem || selectedItemType !== 'note') return;
	const title = document.getElementById('itemTitle').textContent;
	const content = document.getElementById('itemContent').value;
	try {
		const res = await secureFetch(`/api/notion/${selectedItem.noteIdx}`, {
			method: 'PUT',
			headers: new Headers({
				'Content-Type': 'application/json',
				[csrfHeader]: csrfToken
			}),
			body: JSON.stringify({ title, content })
		});
		if (res.ok) {
			showMessage('노트가 저장되었습니다.');
			cancelEdit();
			loadData();
		} else {
			showMessage('저장 실패');
		}
	} catch {
		showMessage('저장 중 오류가 발생했습니다.');
	}
}
function cancelEdit() {
	if (!selectedItem) return;
	showNoteContent(selectedItem);
}

// 삭제/다운로드
async function deleteNote() {
	if (!selectedItem || selectedItemType !== 'note') return;
	if (!confirm('정말로 이 노트를 삭제하시겠습니까?')) return;
	try {
		const res = await secureFetch(`/api/notion/${selectedItem.noteIdx}`, {
			method: 'DELETE',
			headers: new Headers({ [csrfHeader]: csrfToken })
		});
		if (res.ok) {
			showMessage('노트가 삭제되었습니다.');
			selectedItem = null;
			selectedItemType = null;
			clearContent();
			loadData();
		} else {
			showMessage('삭제 실패');
		}
	} catch {
		showMessage('삭제 중 오류가 발생했습니다.');
	}
}
function downloadNote() {
	if (!selectedItem) return;
	window.open(`/api/notion/download/${selectedItem.noteIdx}`, '_blank');
}
async function deleteFile() {
	if (!selectedItem || selectedItemType !== 'file') return;
	if (!confirm('정말로 이 파일을 삭제하시겠습니까?')) return;
	try {
		const res = await secureFetch(`/api/files/delete/${selectedItem.id}`, {
			method: 'DELETE',
			headers: new Headers({ [csrfHeader]: csrfToken })
		});
		if (res.ok) {
			showMessage('파일이 삭제되었습니다.');
			selectedItem = null;
			selectedItemType = null;
			clearContent();
			loadData();
		} else {
			showMessage('삭제 실패');
		}
	} catch {
		showMessage('파일 삭제 중 오류가 발생했습니다.');
	}
}
function downloadFile() {
	if (!selectedItem) return;
	window.open(`/api/files/download/${selectedItem.id}`, '_blank');
}

// 파일 업로드
function uploadFile() {
	document.getElementById('fileInput').click();
}
async function handleFileUpload(files) {
	for (let file of files) {
		const formData = new FormData();
		formData.append('file', file);
		try {
			const res = await secureFetch('/api/files/upload', {
				method: 'POST',
				headers: new Headers({ [csrfHeader]: csrfToken }),
				body: formData
			});
			if (res.ok) {
				showMessage(`${file.name} 업로드 완료`);
			} else {
				showMessage(`${file.name} 업로드 실패`);
			}
		} catch {
			showMessage(`${file.name} 업로드 실패`);
		}
	}
	loadData();
}

// 드래그 앤 드롭
function handleDragStart(e, item, type) {
	e.dataTransfer.setData('text/plain', JSON.stringify({ item, type }));
	e.currentTarget.classList.add('dragging');
}
function handleDragEnd(e) {
	e.currentTarget.classList.remove('dragging');
}
function handleDragOver(e) {
	e.preventDefault();
	e.currentTarget.classList.add('drop-target');
}
function handleDragLeave(e) {
	e.currentTarget.classList.remove('drop-target');
}
async function handleNoteDrop(e, targetFolderId) {
	e.preventDefault();
	e.currentTarget.classList.remove('drop-target');
	try {
		const data = JSON.parse(e.dataTransfer.getData('text/plain'));
		const { item, type } = data;
		if (type === 'note') {
			const formData = new FormData();
			formData.append('noteId', item.noteIdx);
			formData.append('targetFolderId', targetFolderId);
			const res = await secureFetch('/api/unified/notes/move', {
				method: 'PUT',
				headers: new Headers({ [csrfHeader]: csrfToken }),
				body: formData
			});
			const result = await res.json();
			if (result.success) {
				showMessage('노트가 이동되었습니다.');
				loadData();
			} else {
				showMessage('이동 실패: ' + (result.message || ''));
			}
		}
	} catch (err) {
		console.error('노트 드롭 처리 오류:', err);
		showMessage('노트 이동 중 오류가 발생했습니다.');
	}
}
async function handleFileDrop(e, targetFolderId) {
	e.preventDefault();
	e.currentTarget.classList.remove('drop-target');
	try {
		const data = JSON.parse(e.dataTransfer.getData('text/plain'));
		const { item, type } = data;
		if (type === 'file') {
			const formData = new FormData();
			formData.append('fileId', item.id);
			formData.append('targetFolderId', targetFolderId);
			const res = await secureFetch('/api/folders/move-file', {
				method: 'PUT',
				headers: new Headers({ [csrfHeader]: csrfToken }),
				body: formData
			});
			const result = await res.json();
			if (result.success) {
				showMessage('파일이 이동되었습니다.');
				loadData();
			} else {
				showMessage('이동 실패: ' + (result.message || ''));
			}
		}
	} catch (err) {
		console.error('파일 드롭 처리 오류:', err);
		showMessage('파일 이동 중 오류가 발생했습니다.');
	}
}

// 컨텍스트 메뉴 동작
function renameItem() {
	const menu = document.getElementById('contextMenu');
	const itemType = menu.dataset.itemType;
	const itemId = menu.dataset.itemId;
	const newName = prompt('새 이름을 입력하세요:');
	if (newName && newName.trim()) {
		showMessage('이름 변경 기능을 구현해주세요.');
	}
	hideContextMenu();
}
function moveItem() {
	showMessage('이동 기능을 구현해주세요.');
	hideContextMenu();
}

// 파일 폴더 CRUD (Mongo)
async function deleteFolder() {
	if (!selectedItem || selectedItemType !== 'folder') {
		alert('삭제할 폴더를 선택해주세요.');
		return;
	}
	if (!confirm('정말로 이 폴더를 삭제하시겠습니까?')) return;
	try {
		const res = await secureFetch(`/api/folders/${selectedItem.id}`, {
			method: 'DELETE',
			headers: new Headers({ [csrfHeader]: csrfToken })
		});
		const result = await res.json();
		if (result.success) {
			showMessage('폴더가 삭제되었습니다.');
			selectedItem = null;
			selectedItemType = null;
			clearContent();
			loadData();
		} else {
			showMessage('오류: ' + (result.message || ''));
		}
	} catch (e) {
		console.error('폴더 삭제 실패:', e);
		showMessage('폴더 삭제 중 오류가 발생했습니다.');
	}
}

async function renameFolder() {
	if (!selectedItem || selectedItemType !== 'folder') {
		alert('이름을 변경할 폴더를 선택해주세요.');
		return;
	}
	const newName = prompt('새 폴더 이름을 입력하세요:', selectedItem.name || '');
	if (!newName || !newName.trim()) return;
	try {
		const formData = new FormData();
		formData.append('newName', newName.trim());
		const res = await secureFetch(`/api/folders/rename/${selectedItem.id}`, {
			method: 'PUT',
			headers: new Headers({ [csrfHeader]: csrfToken }),
			body: formData
		});
		const result = await res.json();
		if (result.success) {
			showMessage('폴더 이름이 변경되었습니다.');
			loadData();
		} else {
			showMessage('오류: ' + (result.message || ''));
		}
	} catch (e) {
		console.error('폴더 이름 변경 실패:', e);
		showMessage('폴더 이름 변경 중 오류가 발생했습니다.');
	}
}

// 유틸
function filterItems(keyword) {
	const items = document.querySelectorAll('.note-item, .file-item, .folder-item');
	const lower = (keyword || '').toLowerCase();
	items.forEach(item => {
		const text = item.textContent.toLowerCase();
		item.style.display = text.includes(lower) ? 'flex' : 'none';
	});
}
function clearContent() {
	document.getElementById('itemTitle').textContent = '파일이나 노트를 선택해주세요';
	document.getElementById('itemContent').value = '';
	document.getElementById('buttonContainer').innerHTML = `
      <div class="welcome-message">
        <p>📚 왼쪽에서 파일이나 정리본을 선택하시면<br>내용을 확인하고 편집할 수 있습니다</p>
      </div>
    `;
}
function showMessage(message) {
	alert(message);
}
function getFileIcon(filename) {
	const ext = (filename || '').toLowerCase().split('.').pop();
	const icons = { pdf: '📕', docx: '📘', doc: '📘', txt: '📄', md: '📝' };
	return icons[ext] || '📄';
}
function escapeHtml(str) {
	return (str || '').replace(/[&<>"']/g, s => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[s]));
}