/* NotionManager.js — 2025-10-13 (수정본 전체)
 * 탭: 'notes'(정리본) / 'files'(원본파일)
 * 백엔드 API 규약:
 *   - 노트 트리     : GET  /api/unified/notes/tree  -> { folders, rootNotes }
 *   - 파일 트리     : GET  /api/unified/files/tree  -> { folders, rootFiles }
 *   - 파일 업로드   : POST /api/files/upload  (FormData: file[, folderId])
 *   - 파일 미리보기 : GET  /api/files/preview/{gridfsId}
 *   - 파일 다운로드 : GET  /api/files/download/{gridfsId}
 *   - 파일 삭제     : DELETE /api/files/delete/{gridfsId}
 *   - 파일 이동     : PUT /api/folders/move-file (FormData: fileId(문서ID), targetFolderId?)
 *   - 파일 폴더 생성: POST /api/folders?folderName=...&parentFolderId=...
 *   - 파일 폴더 이름변경: PUT /api/folders/{id}/rename?newName=...
 *   - 파일 폴더 삭제: DELETE /api/folders/{id}
 *   - 노트 수정     : PUT  /api/notion/{noteId} (JSON: {title, content, isPublic?})
 *   - 노트 삭제     : DELETE /api/notion/{noteId}
 *   - 노트 이동     : PUT  /api/unified/notes/move (FormData: noteId, targetFolderId)
 *   - 노트 다운로드 : GET  /api/notion/download/{noteId}
 */

'use strict';

// ──────────────────────────────────────────────────────────────
// CSRF & secureFetch
// ──────────────────────────────────────────────────────────────
const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';

window.secureFetch = async function(url, options = {}) {
	const headers = options.headers instanceof Headers ? options.headers : new Headers(options.headers || {});
	if (csrfToken) headers.set(csrfHeader, csrfToken);
	options.headers = headers;
	options.credentials = options.credentials || 'same-origin';
	options.cache = options.cache || 'no-store';
	return fetch(url, options);
};

// ──────────────────────────────────────────────────────────────
let currentTab = 'notes';                 // 'notes' | 'files'
let selectedItem = null;                  // 현재 선택 항목
let selectedItemType = null;              // 'note' | 'file' | 'noteFolder' | 'folder'
let selectedItems = [];  // [{type:'file', item: {...}}, ...]
let dragging = false;

const itemsData = {
	notes: [],
	noteFolders: [],
	files: [],
	fileFolders: [],
	rootNotes: [],
	rootFiles: []
};

// ──────────────────────────────────────────────────────────────
// 초기화
// ──────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
	setupEventListeners();
	bindGlobalButtons();
	loadData();
});

function bindGlobalButtons() {
	document.getElementById('createFolderBtn')?.addEventListener('click', createFolder);
	document.getElementById('uploadBtn')?.addEventListener('click', uploadFile);
	document.getElementById('downloadBtn')?.addEventListener('click', downloadSelected);
};


function setupEventListeners() {
	// 탭 버튼
	document.querySelectorAll('.tab-button').forEach(btn => {
		btn.addEventListener('click', () => switchTab(btn.dataset.tab));
	});

	// 검색
	document.getElementById('searchInput')?.addEventListener('input', function() {
		filterItems(this.value);
	});

	// 파일 업로드 input
	document.getElementById('fileInput')?.addEventListener('change', function() {
		handleFileUpload(this.files);
	});

	// 컨텍스트 메뉴 숨기기
	document.addEventListener('click', hideContextMenu);

	// 드래그 전역 상태
	document.addEventListener('dragstart', () => { dragging = true; }, true);
	document.addEventListener('dragend', () => { setTimeout(() => dragging = false, 0); }, true);
}

// ──────────────────────────────────────────────────────────────
// 탭 전환 & 데이터 로드
// ──────────────────────────────────────────────────────────────
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

function loadData() {
	if (currentTab === 'notes') loadNotesWithFolders();
	else loadFilesWithFolders();
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
			itemsData.fileFolders = Array.isArray(data.folders) ? data.folders : [];
			const rf = data.rootFiles ?? data.root_files ?? data.rootfiles ?? null;
			itemsData.files = normalizeArray(rf);
			console.log('files.tree:', { folders: itemsData.fileFolders, rootFiles: itemsData.files });
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


function normalizeArray(x) {
	if (!x) return [];
	if (Array.isArray(x)) return x;
	if (typeof x === 'object') return Object.values(x); // 객체면 값 배열로
	return [];
}


// ──────────────────────────────────────────────────────────────
// 목록 렌더링
// ──────────────────────────────────────────────────────────────
function renderItemList() {
	const listContainer = document.getElementById('itemList');
	if (!listContainer) return;
	listContainer.innerHTML = '';

	if (currentTab === 'notes') {
		// 루트 노트 헤더 (항상 표시)
		const header = document.createElement('div');
		header.className = 'folder-header';
		header.textContent = '📝 루트 노트';
		listContainer.appendChild(header);

		if (itemsData.notes?.length) {
			itemsData.notes.forEach(note => listContainer.appendChild(createNoteElement(note, 0)));
		} else {
			const empty = document.createElement('div');
			empty.className = 'empty-section';
			empty.textContent = '루트에 노트가 없습니다';
			listContainer.appendChild(empty);
		}

		// 폴더 트리
		itemsData.noteFolders?.forEach(folder => renderNoteFolderTree(listContainer, folder, 0));

	} else {
		// 루트 파일 헤더 (항상 표시)
		const header = document.createElement('div');
		header.className = 'folder-header';
		header.textContent = '📁 루트 파일';
		listContainer.appendChild(header);


		if (Array.isArray(itemsData.files) && itemsData.files.length > 0) {
			itemsData.files.forEach(file => listContainer.appendChild(createFileElement(file, 0)));
		} else {
			const empty = document.createElement('div');
			empty.className = 'empty-section';
			empty.textContent = '루트에 파일이 없습니다';
			listContainer.appendChild(empty);
		}

		// 파일 폴더 트리
		itemsData.fileFolders?.forEach(folder => renderFileFolderTree(listContainer, folder, 0));
	}
}

// 노트 폴더 트리
function renderNoteFolderTree(container, folder, depth) {
	container.appendChild(createNoteFolderElement(folder, depth));

	// 하위 노트
	if (Array.isArray(folder.notes) && folder.notes.length) {
		folder.notes.forEach(note => container.appendChild(createNoteElement(note, depth + 1)));
	}
	// 하위 폴더
	if (Array.isArray(folder.subfolders) && folder.subfolders.length) {
		folder.subfolders.forEach(sub => renderNoteFolderTree(container, sub, depth + 1));
	}
}

// 파일 폴더 트리
function renderFileFolderTree(container, folder, depth) {
	container.appendChild(createFolderElement(folder, depth));

	// 하위 파일
	if (Array.isArray(folder.files) && folder.files.length) {
		folder.files.forEach(file => container.appendChild(createFileElement(file, depth + 1)));
	}
	// 하위 폴더
	if (Array.isArray(folder.subfolders) && folder.subfolders.length) {
		folder.subfolders.forEach(sub => renderFileFolderTree(container, sub, depth + 1));
	}
}

// ──────────────────────────────────────────────────────────────
// 요소 생성
// ──────────────────────────────────────────────────────────────
function createNoteFolderElement(folder, depth = 0) {
	const div = document.createElement('div');
	div.className = 'folder-item';
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
    📁 ${escapeHtml(folder.folderName || '')}
    <span class="folder-actions">
      <button class="action-btn" onclick="renameNoteFolderPrompt(${folder.folderId})">✏️ 이름변경</button>
      <button class="action-btn" onclick="deleteNoteFolder(${folder.folderId})">🗑️ 삭제</button>
    </span>
  `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectFolder(folder, div, 'noteFolder');
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, folder, 'noteFolder'));

	// 노트 드롭 대상
	div.addEventListener('dragover', handleDragOver);
	div.addEventListener('drop', (e) => handleNoteDrop(e, folder.folderId));
	div.addEventListener('dragleave', handleDragLeave);

	return div;
}

function createNoteElement(note, depth = 0) {
	const div = document.createElement('div');
	div.className = 'note-item';
	div.draggable = true;
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `📄 ${escapeHtml(note.title || '')}`;
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

function createFolderElement(folder, depth = 0) {
	const div = document.createElement('div');
	div.className = 'folder-item';
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
    📁 ${escapeHtml(folder.folderName || '')}
    <span class="folder-actions">
      <button class="action-btn" onclick="renameFolder()">✏️ 이름변경</button>
      <button class="action-btn" onclick="deleteFolder()">🗑️ 삭제</button>
    </span>
  `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		selectFolder(folder, div, 'folder');
	});
	div.addEventListener('contextmenu', (e) => showContextMenu(e, folder, 'folder'));

	// 파일 드롭 대상
	div.addEventListener('dragover', handleDragOver);
	div.addEventListener('drop', (e) => handleFileDrop(e, folder.id));
	div.addEventListener('dragleave', handleDragLeave);

	return div;
}

function toggleMultiFileSelection({ item, el }) {
	const idx = selectedItems.findIndex(x => (x.item.id || x.item._id) === (item.id || item._id));
	if (idx >= 0) {
		selectedItems.splice(idx, 1);
		el.classList.remove('selected');
	} else {
		selectedItems.push({ type: 'file', item, el });
		el.classList.add('selected');
	}
	// 우측 버튼 영역 업데이트 (멀티 선택용)
	updateButtons('multi');
}
function clearMultiSelection() {
	selectedItems.forEach(({ el }) => el?.classList.remove('selected'));
	selectedItems = [];
}


function createFileElement(file, depth = 0) {
	const div = document.createElement('div');
	div.className = 'file-item';
	div.draggable = true;
	div.style.marginLeft = `${depth * 20}px`;
	div.innerHTML = `
    ${file.fileIcon || getFileIcon(file.originalName || '')}
    ${escapeHtml(file.originalName || '')}
  `;
	div.addEventListener('click', (e) => {
		if (dragging) return;
		e.stopPropagation();
		const multi = e.ctrlKey || e.metaKey;
		if (multi && currentTab === 'files') {
			toggleMultiFileSelection({ item: file, el: div });
		} else {
			clearMultiSelection();
			selectFile(file, div);
		}
	});

	div.addEventListener('contextmenu', (e) => showContextMenu(e, file, 'file'));
	div.addEventListener('dragstart', (e) => handleDragStart(e, file, 'file'));
	div.addEventListener('dragend', handleDragEnd);
	return div;
}

// ──────────────────────────────────────────────────────────────
// 선택/컨텍스트
// ──────────────────────────────────────────────────────────────
function updateSelectedState(el) {
	document.querySelectorAll('.note-item, .file-item, .folder-item')
		.forEach(e => e.classList.remove('selected'));
	el?.classList.add('selected');
}

function selectNote(note, el) { selectedItem = note; selectedItemType = 'note'; updateSelectedState(el); showNoteContent(note); }
function selectFile(file, el) { selectedItem = file; selectedItemType = 'file'; updateSelectedState(el); showFileContent(file); }
function selectFolder(folder, el, type) { selectedItem = folder; selectedItemType = type; updateSelectedState(el); showFolderContent(folder); }

function showContextMenu(e, item, type) {
	e.preventDefault();
	const menu = document.getElementById('contextMenu');
	if (!menu) return;
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

// ──────────────────────────────────────────────────────────────
async function showNoteContent(note) {
	const titleEl = document.getElementById('itemTitle');
	const contentEl = document.getElementById('itemContent');
	if (!titleEl || !contentEl) return;

	titleEl.textContent = note.title || '(제목 없음)';
	contentEl.value = note.content || '';
	contentEl.classList.add('readonly');
	contentEl.readOnly = true;

	updateButtons('note');
}

async function showFileContent(file) {
	const titleEl = document.getElementById('itemTitle');
	const contentEl = document.getElementById('itemContent');
	if (!titleEl || !contentEl) return;

	titleEl.textContent = file.originalName || '(파일)';
	contentEl.value = '불러오는 중...';
	try {
		// ✅ gridfsId로 미리보기
		const res = await secureFetch(`/api/files/preview/${file.gridfsId}`);
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
	if (!titleEl || !contentEl) return;

	titleEl.textContent = `📁 ${folder.folderName || '(폴더)'}`;
	contentEl.value = '폴더가 선택되었습니다.';
	contentEl.classList.add('readonly');
	contentEl.readOnly = true;

	updateButtons('folder');
}

// ──────────────────────────────────────────────────────────────
// 버튼 UI
// ──────────────────────────────────────────────────────────────
function updateButtons(type) {
	const container = document.getElementById('buttonContainer');
	if (!container) return;
	container.innerHTML = '';

	if (type === 'note') {
		container.innerHTML = `
      <button class="btn-primary"  onclick="editNote()">✏️ 수정하기</button>
      <button id="saveBtn"   class="btn-success hidden"   onclick="saveNote()">💾 저장</button>
      <button id="cancelBtn" class="btn-secondary hidden" onclick="cancelEdit()">❌ 취소</button>
      <button class="btn-secondary" onclick="downloadNote()">💾 다운로드</button>
      <button class="btn-danger"    onclick="deleteNote()">🗑️ 삭제</button>
    `;
	} else if (type === 'file') {
		container.innerHTML = `
      <button class="btn-secondary" onclick="downloadFile()">💾 다운로드</button>
      <button class="btn-danger"    onclick="deleteFile()">🗑️ 삭제</button>
    `;
	} else if (type === 'folder') {
		container.innerHTML = `
      <button class="btn-secondary" onclick="renameFolder()">✏️ 이름변경</button>
      <button class="btn-danger"    onclick="deleteFolder()">🗑️ 삭제</button>
	  <button class="btn-warning"   onclick="downloadSelected()">📦 ZIP으로 다운로드</button>
    `;
	} else if (type === 'multi') {
		// 여러 파일을 선택한 경우 ZIP 다운로드 제공
		container.innerHTML = `
	      <button class="btn-warning" onclick="downloadSelected()">📦 ZIP으로 다운로드 (${selectedItems.length}개)</button>
	      <button class="btn-secondary" onclick="clearMultiSelection()">❌ 선택 해제</button>
	    `;
	}
}




// ──────────────────────────────────────────────────────────────
// 노트 편집/저장/취소/삭제/다운로드
// ──────────────────────────────────────────────────────────────
function editNote() {
	const contentEl = document.getElementById('itemContent');
	const titleEl = document.getElementById('itemTitle');
	if (!contentEl || !titleEl) return;

	contentEl.classList.remove('readonly');
	contentEl.readOnly = false;
	titleEl.classList.remove('readonly');

	document.querySelector('.btn-primary')?.classList.add('hidden');
	document.getElementById('saveBtn')?.classList.remove('hidden');
	document.getElementById('cancelBtn')?.classList.remove('hidden');

	contentEl.focus();
}

async function saveNote() {
	if (!selectedItem || selectedItemType !== 'note') return;
	const title = document.getElementById('itemTitle').textContent;
	const content = document.getElementById('itemContent').value;

	try {
		const res = await secureFetch(`/api/notion/${selectedItem.noteIdx}`, {
			method: 'PUT',
			headers: new Headers({ 'Content-Type': 'application/json', [csrfHeader]: csrfToken }),
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
	if (!selectedItem || selectedItemType !== 'note') return;
	window.open(`/api/notion/download/${selectedItem.noteIdx}`, '_blank');
}

// ──────────────────────────────────────────────────────────────
// 파일 삭제/다운로드/업로드/이동
// ──────────────────────────────────────────────────────────────
async function deleteFile() {
	if (!selectedItem || selectedItemType !== 'file') return;
	if (!confirm('정말로 이 파일을 삭제하시겠습니까?')) return;

	try {
		// ✅ gridfsId로 삭제
		const res = await secureFetch(`/api/files/delete/${selectedItem.gridfsId}`, {
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
	if (!selectedItem || selectedItemType !== 'file') return;
	// ✅ gridfsId로 다운로드
	window.open(`/api/files/download/${selectedItem.gridfsId}`, '_blank');
}

// ✅ 선택된 항목을 ZIP으로 다운로드 (폴더 또는 멀티 선택)
async function downloadSelected() {
	if (currentTab !== 'files') {
		showMessage('원본파일 탭에서만 ZIP 다운로드가 가능합니다.');
		return;
	}
	// 1) 멀티 선택이 있으면 그걸 우선
	let ids = [];
	if (selectedItems.length > 0) {
		ids = selectedItems
			.filter(x => x.type === 'file' && (x.item.gridfsId || x.item.gridFsId || x.item.mongoDocId))
			.map(x => x.item.gridfsId || x.item.gridFsId || x.item.mongoDocId);
	} else if (selectedItem && selectedItemType === 'folder') {
		// 2) 폴더를 선택한 경우: 폴더 트리를 재귀적으로 순회하여 gridfsId 수집
		ids = collectGridIdsFromFolder(selectedItem);
	} else if (selectedItem && selectedItemType === 'file') {
		// 3) 단일 파일 선택: 바로 단일 다운로드로 처리
		window.open(`/api/files/download/${selectedItem.gridfsId}`, '_blank');
		return;
	}
	if (!ids || ids.length === 0) {
		showMessage('다운로드할 파일이 없습니다.');
		return;
	}
	try {
		const res = await secureFetch('/api/files/download-zip', {
			method: 'POST',
			headers: new Headers({
				'Content-Type': 'application/json',
				[csrfHeader]: csrfToken
			}),
			body: JSON.stringify(ids)
		});
		if (!res.ok) {
			showMessage('ZIP 다운로드 요청이 실패했습니다.');
			return;
		}
		const blob = await res.blob();
		const cd = res.headers.get('Content-Disposition') || '';
		const fname = (cd.match(/filename\*=UTF-8''([^;]+)/)?.[1]) || 'files.zip';
		const url = window.URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = decodeURIComponent(fname);
		document.body.appendChild(a);
		a.click();
		a.remove();
		window.URL.revokeObjectURL(url);
	} catch (e) {
		console.error('ZIP 다운로드 오류:', e);
		showMessage('ZIP 다운로드 중 오류가 발생했습니다.');
	}
}
// 폴더 내 모든 파일(gridfsId) 수집
function collectGridIdsFromFolder(folder) {
	const ids = [];
	const walk = (f) => {
		if (Array.isArray(f.files)) {
			f.files.forEach(file => {
				const gid = file.gridfsId || file.gridFsId || file.mongoDocId;
				if (gid) ids.push(gid);
			});
		}
		if (Array.isArray(f.subfolders)) {
			f.subfolders.forEach(walk);
		}
	};
	walk(folder);
	return ids;
}

function uploadFile() {
	document.getElementById('fileInput')?.click();
}

async function handleFileUpload(files) {
	if (!files || !files.length) return;

	// ✅ 업로드 대상 폴더 결정
	let targetFolderId = null;
	if (currentTab === 'files') {
		if (selectedItemType === 'folder' && selectedItem?.id) {
			targetFolderId = selectedItem.id;                // 폴더 선택 → 그 폴더로
		} else if (selectedItemType === 'file') {
			// 파일 선택 → 그 파일이 속한 폴더로 (없으면 루트)
			targetFolderId = selectedItem.folderId || null;
		}
	}

	for (const file of files) {
		const formData = new FormData();
		formData.append('file', file);
		if (targetFolderId) formData.append('folderId', targetFolderId); // ✅ 폴더 지정 업로드

		try {
			const res = await secureFetch('/api/files/upload', {
				method: 'POST',
				headers: new Headers({ [csrfHeader]: csrfToken }),
				body: formData
			});
			if (res.ok) showMessage(`${file.name} 업로드 완료`);
			else showMessage(`${file.name} 업로드 실패`);
		} catch {
			showMessage(`${file.name} 업로드 실패`);
		}
	}
	loadData(); // 업로드 반영
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
			formData.append('fileId', item.id || item._id); // ✅ 이동은 문서 ID
			if (targetFolderId) formData.append('targetFolderId', targetFolderId);

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

// ──────────────────────────────────────────────────────────────
// 파일 폴더 CRUD (Mongo)
// ──────────────────────────────────────────────────────────────
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
	const newName = prompt('새 폴더 이름을 입력하세요:', selectedItem.folderName || '');
	if (!newName || !newName.trim()) return;
	try {
		const res = await secureFetch(`/api/folders/${selectedItem.id}/rename?newName=${encodeURIComponent(newName.trim())}`, {
			method: 'PUT',
			headers: new Headers({ [csrfHeader]: csrfToken })
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

// ──────────────────────────────────────────────────────────────
// 노트 폴더 (RDB; UnifiedFolderController)
// ──────────────────────────────────────────────────────────────
async function createFolder() {
	const name = prompt('새 폴더 이름을 입력하세요:');
	if (!name || !name.trim()) return;
	try {
		if (currentTab === 'files') {
			// 파일 폴더
			const params = new URLSearchParams();
			params.set('folderName', name.trim());
			if (selectedItemType === 'folder' && selectedItem?.id) {
				params.set('parentFolderId', selectedItem.id);
			}
			const res = await secureFetch(`/api/folders?${params.toString()}`, { method: 'POST' });
			const json = await res.json();
			json.success ? (showMessage('폴더가 생성되었습니다.'), loadData())
				: showMessage(json.message || '생성 실패');
		} else {
			// 노트 폴더
			const params = new URLSearchParams();
			params.set('folderName', name.trim());
			if (selectedItemType === 'noteFolder' && selectedItem?.folderId) {
				params.set('parentFolderId', selectedItem.folderId);
			}
			const res = await secureFetch(`/api/unified/notes/folder?${params.toString()}`, { method: 'POST' });
			const json = await res.json();
			json.success ? (showMessage('노트 폴더가 생성되었습니다.'), loadData())
				: showMessage(json.message || '생성 실패');
		}
	} catch (e) {
		console.error('폴더 생성 오류:', e);
		showMessage('폴더 생성 중 오류가 발생했습니다.');
	}
}

async function renameNoteFolderPrompt(folderId) {
	const newName = prompt('새 폴더 이름을 입력하세요:');
	if (!newName || !newName.trim()) return;
	// 노트 폴더 이름변경 엔드포인트가 없다면 서버 구현 필요
	showMessage('노트 폴더 이름 변경 기능은 서버 엔드포인트 추가가 필요합니다.');
}

async function deleteNoteFolder(folderId) {
	if (!confirm('정말로 이 노트 폴더를 삭제하시겠습니까?')) return;
	try {
		const res = await secureFetch(`/api/unified/notes/folder/${folderId}`, {
			method: 'DELETE',
			headers: new Headers({ [csrfHeader]: csrfToken })
		});
		const json = await res.json();
		json.success ? (showMessage('노트 폴더가 삭제되었습니다.'), loadData())
			: showMessage(json.message || '삭제 실패');
	} catch (e) {
		console.error('노트 폴더 삭제 실패:', e);
		showMessage('노트 폴더 삭제 중 오류가 발생했습니다.');
	}
}

// ──────────────────────────────────────────────────────────────
// 유틸
// ──────────────────────────────────────────────────────────────
function filterItems(keyword) {
	const items = document.querySelectorAll('.note-item, .file-item, .folder-item');
	const lower = (keyword || '').toLowerCase();
	items.forEach(item => {
		const text = item.textContent.toLowerCase();
		item.style.display = text.includes(lower) ? 'flex' : 'none';
	});
}

function clearContent() {
	document.getElementById('itemTitle')?.replaceChildren();
	const titleEl = document.getElementById('itemTitle');
	if (titleEl) titleEl.textContent = '파일이나 노트를 선택해주세요';

	const contentEl = document.getElementById('itemContent');
	if (contentEl) contentEl.value = '';

	const btn = document.getElementById('buttonContainer');
	if (btn) btn.innerHTML = `
    <div class="welcome-message">
      <h2>📚 왼쪽에서 파일이나 정리본을 선택하시면<br/>내용을 확인하고 편집할 수 있습니다</h2>
    </div>
  `;
}

function showMessage(message) { alert(message); }

function getFileIcon(filename) {
	const ext = (filename || '').toLowerCase().split('.').pop();
	const icons = { pdf: '📕', docx: '📘', doc: '📘', xlsx: '📗', xls: '📗', pptx: '📙', ppt: '📙', txt: '📄', md: '📝', jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', csv: '📄' };
	return icons[ext] || '📄';
}
window.renderNotionItem = function(item) {
        const titleEl = document.getElementById('itemTitle');
        const contentEl = document.getElementById('itemContent');
        const container = document.querySelector('.notion-content-container') || document.body;

        if (!titleEl || !contentEl) {
            console.warn('itemTitle 또는 itemContent 요소를 찾을 수 없습니다.');
            return;
        }

        titleEl.textContent = item.title || '제목 없음';
        contentEl.value = item.content || '';
        contentEl.setAttribute('readonly', 'readonly');
        contentEl.classList.add('readonly');

        // 액션 버튼 컨테이너 준비
        let actionArea = document.getElementById('itemActions');
        if (!actionArea) {
            actionArea = document.createElement('div');
            actionArea.id = 'itemActions';
            actionArea.className = 'item-actions';
            // action area를 title 아래 또는 content 위에 배치
            titleEl.insertAdjacentElement('afterend', actionArea);
        }

        // 항상 동일한 버튼을 렌더
        actionArea.innerHTML = '';
        const editBtn = document.createElement('button');
        editBtn.id = 'editBtn';
        editBtn.type = 'button';
        editBtn.textContent = '수정';

        const saveBtn = document.createElement('button');
        saveBtn.id = 'saveBtn';
        saveBtn.type = 'button';
        saveBtn.textContent = '저장';
        saveBtn.style.display = 'none';

        const cancelBtn = document.createElement('button');
        cancelBtn.id = 'cancelBtn';
        cancelBtn.type = 'button';
        cancelBtn.textContent = '취소';
        cancelBtn.style.display = 'none';

        actionArea.appendChild(editBtn);
        actionArea.appendChild(saveBtn);
        actionArea.appendChild(cancelBtn);

        // 이벤트 핸들러
        editBtn.onclick = () => {
            contentEl.removeAttribute('readonly');
            contentEl.classList.remove('readonly');
            editBtn.style.display = 'none';
            saveBtn.style.display = 'inline-block';
            cancelBtn.style.display = 'inline-block';
            contentEl.focus();
        };

        cancelBtn.onclick = () => {
            contentEl.setAttribute('readonly', 'readonly');
            contentEl.classList.add('readonly');
            editBtn.style.display = 'inline-block';
            saveBtn.style.display = 'none';
            cancelBtn.style.display = 'none';
            contentEl.value = item.content || '';
        };

        saveBtn.onclick = () => {
            const updated = {
                noteIdx: item.noteIdx,
                title: titleEl.textContent,
                content: contentEl.value
            };

            fetch(`/api/unified/notes/${item.noteIdx}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify(updated)
            }).then(res => {
                if (!res.ok) throw new Error('저장 실패');
                return res.json();
            }).then(json => {
                if (json.success) {
                    alert('저장되었습니다.');
                    // 상태 복구
                    contentEl.setAttribute('readonly', 'readonly');
                    contentEl.classList.add('readonly');
                    editBtn.style.display = 'inline-block';
                    saveBtn.style.display = 'none';
                    cancelBtn.style.display = 'none';
                    // 가능하면 목록/트리 갱신 호출
                    if (window.reloadNotionList) window.reloadNotionList();
                } else {
                    alert('저장 실패: ' + (json.message || 'unknown'));
                }
            }).catch(err => {
                console.error(err);
                alert('저장 중 오류가 발생했습니다.');
            });
        };
    };
	// helper : 노트 선택 바인딩 - 실제 프로젝트의 리스트 클릭 로직에서 사용
	    window.bindNotionListSelection = function(listSelector) {
	        const list = document.querySelector(listSelector);
	        if (!list) return;
	        list.addEventListener('click', function(e) {
	            const li = e.target.closest('[data-note-idx]');
	            if (!li) return;
	            const noteIdx = li.getAttribute('data-note-idx');
	            fetch(`/api/unified/notes/${noteIdx}`)
	                .then(r => r.json())
	                .then(payload => {
	                    if (payload && payload.note) {
	                        window.renderNotionItem(payload.note);
	                    }
	                });
	        });
	    };

function escapeHtml(str) {
	return (str || '').replace(/[&<>\"']/g, s => ({ '&': '&', '<': '<', '>': '>', '"': '"', '\'': '\'' }[s]));
}