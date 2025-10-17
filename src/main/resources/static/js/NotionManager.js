// ========================================
// NotionManager.js - 완전판
// ========================================

// ========== 1. CSRF 설정 ==========
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

// ========== 2. 전역 상태 변수 ==========
let currentTab = 'notes';
let itemsData = {
    notes: [],
    noteFolders: [],
    files: [],
    fileFolders: []
};
let selectedItem = null;
let selectedItemType = null; // 'note', 'file', 'folder', 'noteFolder'
let selectedItems = []; // 다중 선택용 [{type, item, el}, ...]
let dragging = false;

// HandsOnTable 인스턴스
let hotInstance = null;

// 편집 모드 백업
let originalContent = '';
let originalTitle = '';

// ========== 3. 초기화 ==========
document.addEventListener('DOMContentLoaded', () => {
    setupTabs();
    setupSearch();
    setupFileInput();
    setupCreateFolder();
    fetchTreeData();
});

// ========== 4. 탭 설정 ==========
function setupTabs() {
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelector('.tab-button.active')?.classList.remove('active');
            btn.classList.add('active');
            currentTab = btn.dataset.tab;
            clearSelection();
            renderItemList();
        });
    });
}

// ========== 5. 검색 설정 ==========
function setupSearch() {
    const input = document.getElementById('searchInput');
    if (!input) return;
    
    let timeout;
    input.addEventListener('input', () => {
        clearTimeout(timeout);
        const keyword = input.value.trim();
        if (!keyword) {
            closeSearchModal();
            return;
        }
        timeout = setTimeout(() => performSearch(keyword.toLowerCase()), 300);
    });
}

// ========== 6. 파일 업로드 설정 ==========
function setupFileInput() {
    const fileInput = document.getElementById('fileInput');
    const uploadBtn = document.getElementById('uploadBtn');
    
    if (!fileInput || !uploadBtn) return;
    
    uploadBtn.addEventListener('click', () => fileInput.click());
    
    fileInput.addEventListener('change', async (e) => {
        const files = Array.from(e.target.files);
        if (!files.length) return;
        
        let targetFolderId = null;
        if (currentTab === 'files' && selectedItemType === 'folder' && selectedItem?.id) {
            targetFolderId = selectedItem.id;
        }
        
        const form = new FormData();
        files.forEach(f => form.append('files', f));
        if (targetFolderId) form.append('folderId', targetFolderId);
        
        try {
            const res = await secureFetch('/api/files/upload', {
                method: 'POST',
                headers: new Headers({ [csrfHeader]: csrfToken }),
                body: form
            });
            
            if (res.ok) {
                showMessage('업로드 성공');
                fetchTreeData();
            } else {
                showMessage('업로드 실패');
            }
        } catch (e) {
            console.error('업로드 오류:', e);
            showMessage('업로드 중 오류 발생');
        }
        
        fileInput.value = '';
    });
}

// ========== 7. 폴더 생성 버튼 설정 ==========
function setupCreateFolder() {
    const btn = document.getElementById('createFolderBtn');
    if (btn) btn.addEventListener('click', createFolder);
}

// ========== 8. 트리 데이터 가져오기 ==========
async function fetchTreeData() {
    try {
        const [notesRes, filesRes] = await Promise.all([
            secureFetch('/api/unified/notes/tree'),
            secureFetch('/api/unified/files/tree')
        ]);
        
        if (notesRes.ok) {
            const data = await notesRes.json();
            itemsData.noteFolders = data.folders || [];
            itemsData.notes = data.rootNotes || [];
        }
        
        if (filesRes.ok) {
            const data = await filesRes.json();
            itemsData.fileFolders = data.folders || [];
            itemsData.files = data.rootFiles || [];
        }
        
        renderItemList();
    } catch (e) {
        console.error('트리 데이터 불러오기 실패:', e);
        showMessage('데이터 로드 실패');
    }
}

// ========== 9. 리스트 렌더링 ==========
function renderItemList() {
    const container = document.getElementById('itemList');
    if (!container) return;
    
    container.innerHTML = '';
    container.className = 'notion-list tree-container';
    
    if (currentTab === 'notes') {
        if (!itemsData.noteFolders.length && !itemsData.notes.length) {
            container.innerHTML = '<p style="text-align:center;color:#999;margin-top:50px;">노트가 없습니다</p>';
            return;
        }
        
        itemsData.noteFolders.forEach(f => container.appendChild(createNoteFolderTreeElement(f, 0)));
        itemsData.notes.forEach(n => container.appendChild(createNoteElement(n, 0)));
    } else {
        if (!itemsData.fileFolders.length && !itemsData.files.length) {
            container.innerHTML = '<p style="text-align:center;color:#999;margin-top:50px;">파일이 없습니다</p>';
            return;
        }
        
        itemsData.fileFolders.forEach(f => container.appendChild(createFileFolderTreeElement(f, 0)));
        itemsData.files.forEach(f => container.appendChild(createFileElement(f, 0)));
    }
}

// ========== 10. 노트 폴더 트리 요소 생성 (재귀) ==========
function createNoteFolderTreeElement(folder, depth) {
    const container = document.createElement('div');
    container.className = 'folder-container';
    container.dataset.folderId = folder.folderId;
    
    const folderItem = document.createElement('div');
    folderItem.className = 'folder-item';
    folderItem.style.paddingLeft = `${depth * 20 + 10}px`;
    folderItem.dataset.folderId = folder.folderId;
    
    const hasChildren = (folder.notes?.length > 0) || (folder.subfolders?.length > 0);
    
    const toggle = document.createElement('span');
    toggle.className = `folder-toggle ${hasChildren ? '' : 'empty'}`;
    toggle.innerHTML = hasChildren ? '▶' : '';
    toggle.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleFolder(container, toggle);
    });
    
    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📁';
    
    const name = document.createElement('span');
    name.className = 'folder-name';
    name.textContent = folder.folderName || '(이름 없음)';
    
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); renameNoteFolderPrompt(${folder.folderId})" title="이름 변경">✏️</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteNoteFolder(${folder.folderId})" title="삭제">🗑️</button>
    `;
    
    folderItem.appendChild(toggle);
    folderItem.appendChild(icon);
    folderItem.appendChild(name);
    folderItem.appendChild(actions);
    
    folderItem.addEventListener('click', (e) => {
        if (dragging) return;
        e.stopPropagation();
        selectFolder(folder, folderItem, 'noteFolder');
    });
    
    folderItem.addEventListener('dragover', handleDragOver);
    folderItem.addEventListener('drop', (e) => handleNoteDrop(e, folder.folderId));
    folderItem.addEventListener('dragleave', handleDragLeave);
    
    container.appendChild(folderItem);
    
    if (hasChildren) {
        const children = document.createElement('div');
        children.className = 'folder-children expanded';
        children.dataset.expanded = 'true';
        
        if (folder.subfolders?.length) {
            folder.subfolders.forEach(sub => children.appendChild(createNoteFolderTreeElement(sub, depth + 1)));
        }
        
        if (folder.notes?.length) {
            folder.notes.forEach(note => children.appendChild(createNoteElement(note, depth + 1)));
        }
        
        container.appendChild(children);
    }
    
    return container;
}

// ========== 11. 파일 폴더 트리 요소 생성 (재귀) ==========
function createFileFolderTreeElement(folder, depth) {
    const container = document.createElement('div');
    container.className = 'folder-container';
    container.dataset.folderId = folder.id;
    
    const folderItem = document.createElement('div');
    folderItem.className = 'folder-item';
    folderItem.style.paddingLeft = `${depth * 20 + 10}px`;
    folderItem.dataset.folderId = folder.id;
    
    const hasChildren = (folder.files?.length > 0) || (folder.subfolders?.length > 0);
    
    const toggle = document.createElement('span');
    toggle.className = `folder-toggle ${hasChildren ? '' : 'empty'}`;
    toggle.innerHTML = hasChildren ? '▶' : '';
    toggle.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleFolder(container, toggle);
    });
    
    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📁';
    
    const name = document.createElement('span');
    name.className = 'folder-name';
    name.textContent = folder.folderName || '(이름 없음)';
    
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); downloadFolder('${folder.id}')" title="다운로드">💾</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); renameFolderPrompt('${folder.id}')" title="이름 변경">✏️</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteFolderPrompt('${folder.id}')" title="삭제">🗑️</button>
    `;
    
    folderItem.appendChild(toggle);
    folderItem.appendChild(icon);
    folderItem.appendChild(name);
    folderItem.appendChild(actions);
    
    folderItem.addEventListener('click', (e) => {
        if (dragging) return;
        e.stopPropagation();
        selectFolder(folder, folderItem, 'folder');
    });
    
    folderItem.addEventListener('dragover', handleDragOver);
    folderItem.addEventListener('drop', (e) => handleFileDrop(e, folder.id));
    folderItem.addEventListener('dragleave', handleDragLeave);
    
    container.appendChild(folderItem);
    
    if (hasChildren) {
        const children = document.createElement('div');
        children.className = 'folder-children expanded';
        children.dataset.expanded = 'true';
        
        if (folder.subfolders?.length) {
            folder.subfolders.forEach(sub => children.appendChild(createFileFolderTreeElement(sub, depth + 1)));
        }
        
        if (folder.files?.length) {
            folder.files.forEach(file => children.appendChild(createFileElement(file, depth + 1)));
        }
        
        container.appendChild(children);
    }
    
    return container;
}

// ========== 12. 노트 요소 생성 ==========
function createNoteElement(note, depth) {
    const div = document.createElement('div');
    div.className = 'note-item';
    div.draggable = true;
    div.style.paddingLeft = `${depth * 20 + 30}px`;
    div.dataset.noteIdx = note.noteIdx;
    
    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📄';
    
    const name = document.createElement('span');
    name.className = 'note-name';
    name.textContent = note.title || '(제목 없음)';
    
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); downloadNote(${note.noteIdx})" title="다운로드">💾</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteNotePrompt(${note.noteIdx})" title="삭제">🗑️</button>
    `;
    
    div.appendChild(icon);
    div.appendChild(name);
    div.appendChild(actions);
    
    div.addEventListener('click', (e) => {
        if (dragging) return;
        e.stopPropagation();
        selectNote(note, div);
    });
    
    div.addEventListener('dragstart', (e) => handleDragStart(e, note, 'note'));
    div.addEventListener('dragend', handleDragEnd);
    
    return div;
}

// ========== 13. 파일 요소 생성 ==========
function createFileElement(file, depth) {
    const div = document.createElement('div');
    div.className = 'file-item';
    div.draggable = true;
    div.style.paddingLeft = `${depth * 20 + 30}px`;
    div.dataset.gridfsId = file.gridfsId;
    
    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = getFileIcon(file.originalName || '');
    
    const name = document.createElement('span');
    name.className = 'file-name';
    name.textContent = file.originalName || '(파일명 없음)';
    
    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); downloadSingleFile('${file.gridfsId}')" title="다운로드">💾</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteFilePrompt('${file.gridfsId}')" title="삭제">🗑️</button>
    `;
    
    div.appendChild(icon);
    div.appendChild(name);
    div.appendChild(actions);
    
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
    
    div.addEventListener('dragstart', (e) => handleDragStart(e, file, 'file'));
    div.addEventListener('dragend', handleDragEnd);
    
    return div;
}

// ========== 14. 폴더 토글 ==========
function toggleFolder(container, toggle) {
    const children = container.querySelector('.folder-children');
    if (!children) return;
    
    const isExpanded = children.dataset.expanded === 'true';
    if (isExpanded) {
        children.classList.remove('expanded');
        children.dataset.expanded = 'false';
        toggle.innerHTML = '▶';
    } else {
        children.classList.add('expanded');
        children.dataset.expanded = 'true';
        toggle.innerHTML = '▼';
    }
}

// ========== 15. 선택 처리 ==========
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
    selectedItemType = type;
    updateSelectedState(el);
    showFolderContent(folder);
}

function updateSelectedState(el) {
    document.querySelectorAll('.note-item, .file-item, .folder-item').forEach(e => e.classList.remove('selected'));
    el?.classList.add('selected');
}

function clearSelection() {
    selectedItem = null;
    selectedItemType = null;
    clearMultiSelection();
    clearContent();
}

// ========== 16. 다중 선택 ==========
function toggleMultiFileSelection({ item, el }) {
    const idx = selectedItems.findIndex(x => x.item.gridfsId === item.gridfsId);
    if (idx >= 0) {
        selectedItems.splice(idx, 1);
        el.classList.remove('selected');
    } else {
        selectedItems.push({ type: 'file', item, el });
        el.classList.add('selected');
    }
    updateButtons('multi');
}

function clearMultiSelection() {
    selectedItems.forEach(({ el }) => el?.classList.remove('selected'));
    selectedItems = [];
}

// ========== 17. 컨텐츠 표시 ==========
async function showNoteContent(note) {
    hideAllViews();
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const welcomeMsg = document.getElementById('welcomeMessage');
    
    if (welcomeMsg) welcomeMsg.style.display = 'none';
    
    titleEl.textContent = note.title || '(제목 없음)';
    contentEl.style.display = 'block';
    contentEl.value = note.content || '';
    contentEl.classList.add('readonly');
    contentEl.readOnly = true;
    
    updateButtons('note');
}

async function showFileContent(file) {
    hideAllViews();
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const previewArea = document.getElementById('previewArea');
    const pdfPreview = document.getElementById('pdfPreview');
    const imagePreview = document.getElementById('imagePreview');
    const sheetContainer = document.getElementById('spreadsheetContainer');
    const welcomeMsg = document.getElementById('welcomeMessage');
    
    if (welcomeMsg) welcomeMsg.style.display = 'none';
    
    titleEl.textContent = file.originalName || '(파일명 없음)';
    
    const ext = getFileExtension(file.originalName || '').toLowerCase();
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'];
    
    if (ext === 'pdf') {
        previewArea.style.display = 'block';
        pdfPreview.style.display = 'block';
        imagePreview.style.display = 'none';
        pdfPreview.src = `/api/files/preview/${file.gridfsId}`;
    } else if (imageExts.includes(ext)) {
        previewArea.style.display = 'block';
        pdfPreview.style.display = 'none';
        imagePreview.style.display = 'block';
        imagePreview.src = `/api/files/download/${file.gridfsId}`;
    } else if (ext === 'csv' || ext === 'xlsx' || ext === 'xls') {
        sheetContainer.style.display = 'block';
        try {
            const res = await secureFetch(`/api/files/preview/${file.gridfsId}`);
            const data = await res.text();
            initSpreadsheet(data);
        } catch (e) {
            console.error('스프레드시트 로드 실패:', e);
            contentEl.style.display = 'block';
            contentEl.value = '스프레드시트를 불러올 수 없습니다.';
        }
    } else {
        contentEl.style.display = 'block';
        contentEl.value = '불러오는 중...';
        try {
            const res = await secureFetch(`/api/files/preview/${file.gridfsId}`);
            contentEl.value = res.ok ? await res.text() : '미리보기를 불러올 수 없습니다.';
        } catch {
            contentEl.value = '파일 로드 중 오류 발생';
        }
        contentEl.classList.add('readonly');
        contentEl.readOnly = true;
    }
    
    updateButtons('file');
}

function showFolderContent(folder) {
    hideAllViews();
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const welcomeMsg = document.getElementById('welcomeMessage');
    
    if (welcomeMsg) welcomeMsg.style.display = 'none';
    
    titleEl.textContent = folder.folderName || '(폴더)';
    contentEl.style.display = 'block';
    contentEl.value = '폴더가 선택되었습니다.\n\n아래 버튼으로 폴더를 관리하세요.';
    contentEl.classList.add('readonly');
    contentEl.readOnly = true;
    
    updateButtons('folder');
}

// ========== 18. 모든 뷰 숨김 ==========
function hideAllViews() {
    document.getElementById('itemContent').style.display = 'none';
    document.getElementById('previewArea').style.display = 'none';
    document.getElementById('spreadsheetContainer').style.display = 'none';
    document.getElementById('pdfPreview').style.display = 'none';
    document.getElementById('imagePreview').style.display = 'none';
    
    if (hotInstance) {
        hotInstance.destroy();
        hotInstance = null;
    }
}

// ========== 19. HandsOnTable 초기화 ==========
function initSpreadsheet(csvData) {
    const container = document.getElementById('spreadsheetContainer');
    if (!container) return;
    
    try {
        const rows = csvData.split('\n').map(r => r.split(','));
        hotInstance = new Handsontable(container, {
            data: rows,
            colHeaders: true,
            rowHeaders: true,
            contextMenu: true,
            licenseKey: 'non-commercial-and-evaluation',
            width: '100%',
            height: '100%'
        });
    } catch (e) {
        console.error('HandsOnTable 초기화 실패:', e);
    }
}

// ========== 20. 버튼 업데이트 ==========
function updateButtons(type) {
    const container = document.getElementById('buttonContainer');
    if (!container) return;
    
    container.innerHTML = '';
    
    if (type === 'note') {
        container.innerHTML = `
            <button class="btn-edit" id="editBtn" onclick="enterEditMode()">✏️ 편집하기</button>
            <button class="btn-save hidden" id="saveBtn" onclick="saveNote()">💾 저장</button>
            <button class="btn-cancel hidden" id="cancelBtn" onclick="cancelEdit()">❌ 취소</button>
            <button class="btn-download" onclick="downloadNote()">📥 다운로드</button>
            <button class="btn-delete" onclick="deleteNotePrompt()">🗑️ 삭제</button>
        `;
    } else if (type === 'file') {
        const ext = getFileExtension(selectedItem?.originalName || '').toLowerCase();
        const editableExts = ['txt', 'md', 'json', 'xml', 'csv', 'log'];
        const isEditable = editableExts.includes(ext);
        
		if (ext === 'csv' || ext === 'xlsx' || ext === 'xls') {
		            container.innerHTML = `
		                <button class="btn-save" onclick="saveSpreadsheet()">💾 저장</button>
		                <button class="btn-download" onclick="downloadFile()">📥 다운로드</button>
		                <button class="btn-delete" onclick="deleteFilePrompt()">🗑️ 삭제</button>
		            `;
		        } else if (isEditable) {
		            container.innerHTML = `
		                <button class="btn-edit" id="editBtn" onclick="enterEditMode()">✏️ 편집하기</button>
		                <button class="btn-save hidden" id="saveBtn" onclick="saveFile()">💾 저장</button>
		                <button class="btn-cancel hidden" id="cancelBtn" onclick="cancelEdit()">❌ 취소</button>
		                <button class="btn-download" onclick="downloadFile()">📥 다운로드</button>
		                <button class="btn-delete" onclick="deleteFilePrompt()">🗑️ 삭제</button>
		            `;
		        } else {
		            container.innerHTML = `
		                <button class="btn-download" onclick="downloadFile()">📥 다운로드</button>
		                <button class="btn-delete" onclick="deleteFilePrompt()">🗑️ 삭제</button>
		            `;
		        }
    } else if (type === 'folder') {
        container.innerHTML = `
            <button class="btn-download" onclick="downloadFolderAsZip()">📦 ZIP 다운로드</button>
            <button class="btn-rename" onclick="renameFolderPrompt()">✏️ 이름 변경</button>
            <button class="btn-delete" onclick="deleteFolderPrompt()">🗑️ 삭제</button>
        `;
    } else if (type === 'multi') {
        container.innerHTML = `
            <button class="btn-download" onclick="downloadSelectedAsZip()">📦 선택 항목 ZIP (${selectedItems.length}개)</button>
            <button class="btn-cancel" onclick="clearMultiSelection()">❌ 선택 해제</button>
        `;
    }
}

// ========== 21. 편집 모드 ==========
function enterEditMode() {
    if (!selectedItem) return;
    
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    
    if (!titleEl || !contentEl) return;
    
    originalTitle = titleEl.textContent;
    originalContent = contentEl.value;
    
    titleEl.contentEditable = 'true';
    titleEl.classList.remove('readonly');
    titleEl.focus();
    
    contentEl.classList.remove('readonly');
    contentEl.readOnly = false;
    
    document.getElementById('editBtn')?.classList.add('hidden');
    document.getElementById('saveBtn')?.classList.remove('hidden');
    document.getElementById('cancelBtn')?.classList.remove('hidden');
}

function cancelEdit() {
    if (!selectedItem) return;
    
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    
    if (!titleEl || !contentEl) return;
    
    titleEl.textContent = originalTitle;
    titleEl.contentEditable = 'false';
    titleEl.classList.add('readonly');
    
    contentEl.value = originalContent;
    contentEl.classList.add('readonly');
    contentEl.readOnly = true;
    
    document.getElementById('editBtn')?.classList.remove('hidden');
    document.getElementById('saveBtn')?.classList.add('hidden');
    document.getElementById('cancelBtn')?.classList.add('hidden');
}

// ========== 22. 저장 함수 ==========
async function saveNote() {
    if (!selectedItem || selectedItemType !== 'note') return;
    
    const title = document.getElementById('itemTitle')?.textContent.trim();
    const content = document.getElementById('itemContent')?.value;
    
    if (!title) {
        showMessage('제목을 입력해주세요.');
        return;
    }
    
    try {
        const res = await secureFetch(`/notion/${selectedItem.noteIdx}`, { method: 'PUT',
            headers: new Headers({
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }),
            body: JSON.stringify({ title, content })
        });
        
        const json = await res.json();
        if (json.success) {
            showMessage('저장되었습니다.');
            cancelEdit();
            fetchTreeData();
        } else {
            showMessage(json.message || '저장 실패');
        }
    } catch (e) {
        console.error('저장 오류:', e);
        showMessage('저장 중 오류 발생');
    }
}

async function saveFile() {
    if (!selectedItem || selectedItemType !== 'file') return;
    
    const content = document.getElementById('itemContent')?.value;
    
    try {
        const res = await secureFetch(`/api/files/update/${selectedItem.gridfsId}`, {
            method: 'PUT',
            headers: new Headers({
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }),
            body: JSON.stringify({ content })
        });
        
        const json = await res.json();
        if (json.success) {
            showMessage('파일이 저장되었습니다.');
            if (json.newGridfsId) {
                selectedItem.gridfsId = json.newGridfsId;
            }
            cancelEdit();
            fetchTreeData();
        } else {
            showMessage(json.message || '저장 실패');
        }
    } catch (e) {
        console.error('저장 오류:', e);
        showMessage('저장 중 오류 발생');
    }
}

async function saveSpreadsheet() {
    if (!selectedItem || selectedItemType !== 'file' || !hotInstance) return;
    
    // HandsOnTable 데이터를 CSV로 변환
    const data = hotInstance.getData();
    const csvContent = data.map(row => row.join(',')).join('\n');
    
    try {
        const res = await secureFetch(`/api/files/update/${selectedItem.gridfsId}`, {
            method: 'PUT',
            headers: new Headers({
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }),
            body: JSON.stringify({ content: csvContent })
        });
        
        const result = await res.json();
        if (result.success) {
            showMessage('스프레드시트가 저장되었습니다.');
            
            if (result.newGridfsId) {
                selectedItem.gridfsId = result.newGridfsId;
            }
            
            fetchTreeData();
        } else {
            showMessage(result.message || '저장 실패');
        }
    } catch (e) {
        console.error('스프레드시트 저장 오류:', e);
        showMessage('저장 중 오류 발생');
    }
}

// ========== 23. 다운로드 함수 ==========
function downloadNote() {
    if (!selectedItem || selectedItemType !== 'note') return;
    window.open(`/notion/download/${selectedItem.noteIdx}`, '_blank');
}

function downloadFile() {
    if (!selectedItem || selectedItemType !== 'file') return;
    window.open(`/api/files/download/${selectedItem.gridfsId}`, '_blank');
}

function downloadSingleFile(gridfsId) {
    window.open(`/api/files/download/${gridfsId}`, '_blank');
}

async function downloadFolder(folderId) {
    try {
        const res = await secureFetch('/api/files/download-zip', {
            method: 'POST',
            headers: new Headers({
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }),
            body: JSON.stringify({
                folderIds: [folderId],
                fileIds: []
            })
        });
        
        if (!res.ok) {
            showMessage('ZIP 다운로드 실패');
            return;
        }
        
        downloadBlob(await res.blob(), res.headers.get('Content-Disposition'));
    } catch (e) {
        console.error('ZIP 다운로드 오류:', e);
        showMessage('다운로드 중 오류 발생');
    }
}

async function downloadFolderAsZip() {
    if (!selectedItem || selectedItemType !== 'folder') return;
    await downloadFolder(selectedItem.id);
}

async function downloadSelectedAsZip() {
    if (selectedItems.length === 0) {
        showMessage('다운로드할 항목을 선택해주세요.');
        return;
    }
    
    const folderIds = [];
    const fileIds = [];
    
    selectedItems.forEach(({ type, item }) => {
        if (type === 'folder') {
            folderIds.push(item.id);
        } else if (type === 'file') {
            fileIds.push(item.id);
        }
    });
    
    try {
        const res = await secureFetch('/api/files/download-zip', {
            method: 'POST',
            headers: new Headers({
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }),
            body: JSON.stringify({ folderIds, fileIds })
        });
        
        if (!res.ok) {
            showMessage('ZIP 다운로드 실패');
            return;
        }
        
        downloadBlob(await res.blob(), res.headers.get('Content-Disposition'));
    } catch (e) {
        console.error('ZIP 다운로드 오류:', e);
        showMessage('다운로드 중 오류 발생');
    }
}

function downloadBlob(blob, contentDisposition) {
    const cd = contentDisposition || '';
    const fname = (cd.match(/filename\*=UTF-8''([^;]+)/)?.[1]) || 'download.zip';
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = decodeURIComponent(fname);
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
}

// ========== 24. 삭제 함수 ==========
async function deleteNotePrompt() {
    if (!selectedItem || selectedItemType !== 'note') return;
    if (!confirm('이 노트를 삭제하시겠습니까?')) return;
    
    try {
        const res = await secureFetch(`/api/notion/${selectedItem.noteIdx}`, {
            method: 'DELETE',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        if (res.ok) {
            showMessage('노트가 삭제되었습니다.');
            clearSelection();
            fetchTreeData();
        } else {
            showMessage('삭제 실패');
        }
    } catch (e) {
        console.error('삭제 오류:', e);
        showMessage('삭제 중 오류 발생');
    }
}

async function deleteFilePrompt(gridfsId) {
    if (!gridfsId && selectedItem && selectedItemType === 'file') {
        gridfsId = selectedItem.gridfsId;
    }
    
    if (!gridfsId) return;
    if (!confirm('이 파일을 삭제하시겠습니까?')) return;
    
    try {
        const res = await secureFetch(`/api/files/delete/${gridfsId}`, {
            method: 'DELETE',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        if (res.ok) {
            showMessage('파일이 삭제되었습니다.');
            clearSelection();
            fetchTreeData();
        } else {
            showMessage('삭제 실패');
        }
    } catch (e) {
        console.error('삭제 오류:', e);
        showMessage('삭제 중 오류 발생');
    }
}

async function deleteFolderPrompt(folderId) {
    if (!folderId && selectedItem && selectedItemType === 'folder') {
        folderId = selectedItem.id;
    }
    
    if (!folderId) return;
    if (!confirm('이 폴더와 하위 항목을 모두 삭제하시겠습니까?')) return;
    
    try {
        const res = await secureFetch(`/api/folders/${folderId}`, {
            method: 'DELETE',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        const result = await res.json();
        if (result.success) {
            showMessage('폴더가 삭제되었습니다.');
            clearSelection();
            fetchTreeData();
        } else {
            showMessage(result.message || '삭제 실패');
        }
    } catch (e) {
        console.error('삭제 오류:', e);
        showMessage('삭제 중 오류 발생');
    }
}

async function deleteNoteFolder(folderId) {
    if (!confirm('이 폴더와 하위 노트를 모두 삭제하시겠습니까?')) return;
    
    try {
        const res = await secureFetch(`/api/unified/notes/folder/${folderId}`, {
            method: 'DELETE',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        const json = await res.json();
        if (json.success) {
            showMessage('폴더가 삭제되었습니다.');
            clearSelection();
            fetchTreeData();
        } else {
            showMessage(json.message || '삭제 실패');
        }
    } catch (e) {
        console.error('삭제 오류:', e);
        showMessage('삭제 중 오류 발생');
    }
}

// ========== 25. 폴더 생성/이름 변경 ==========
async function createFolder() {
    const name = prompt('새 폴더 이름을 입력하세요:');
    if (!name || !name.trim()) return;
    
    try {
        if (currentTab === 'files') {
            const params = new URLSearchParams();
            params.set('folderName', name.trim());
            if (selectedItemType === 'folder' && selectedItem?.id) {
                params.set('parentFolderId', selectedItem.id);
            }
            
            const res = await secureFetch(`/api/folders?${params.toString()}`, {
                method: 'POST',
                headers: new Headers({ [csrfHeader]: csrfToken })
            });
            
            const json = await res.json();
            json.success ? (showMessage('폴더 생성 성공'), fetchTreeData()) : showMessage(json.message);
        } else {
            const params = new URLSearchParams();
            params.set('folderName', name.trim());
            if (selectedItemType === 'noteFolder' && selectedItem?.folderId) {
                params.set('parentFolderId', selectedItem.folderId);
            }
            
            const res = await secureFetch(`/api/unified/notes/folder?${params.toString()}`, {
                method: 'POST',
                headers: new Headers({ [csrfHeader]: csrfToken })
            });
            
            const json = await res.json();
            json.success ? (showMessage('폴더 생성 성공'), fetchTreeData()) : showMessage(json.message);
        }
    } catch (e) {
        console.error('폴더 생성 오류:', e);
        showMessage('폴더 생성 실패');
    }
}

async function renameFolderPrompt(folderId) {
    if (!folderId && selectedItem && selectedItemType === 'folder') {
        folderId = selectedItem.id;
    }
    
    if (!folderId) return;
    
    const newName = prompt('새 폴더 이름:', selectedItem?.folderName || '');
    if (!newName || !newName.trim()) return;
    
    try {
        const res = await secureFetch(`/api/folders/${folderId}/rename?newName=${encodeURIComponent(newName.trim())}`, {
            method: 'PUT',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        const result = await res.json();
        if (result.success) {
            showMessage('이름 변경 성공');
            fetchTreeData();
        } else {
            showMessage(result.message || '이름 변경 실패');
        }
    } catch (e) {
        console.error('이름 변경 오류:', e);
        showMessage('이름 변경 실패');
    }
}

async function renameNoteFolderPrompt(folderId) {
    const newName = prompt('새 폴더 이름을 입력하세요:');
    if (!newName || !newName.trim()) return;
    
    try {
        const res = await secureFetch(`/api/unified/notes/folder/${folderId}/rename?newName=${encodeURIComponent(newName.trim())}`, {
            method: 'PUT',
            headers: new Headers({ [csrfHeader]: csrfToken })
        });
        
        const result = await res.json();
        if (result.success) {
            showMessage('노트 폴더 이름 변경 성공');
            fetchTreeData();
        } else {
            showMessage(result.message || '이름 변경 실패');
        }
    } catch (e) {
        console.error('이름 변경 오류:', e);
        showMessage('이름 변경 실패');
    }
}

// ========== 26. 드래그 앤 드롭 ==========
function handleDragStart(e, item, type) {
    dragging = true;
    e.dataTransfer.setData('text/plain', JSON.stringify({ item, type }));
    e.currentTarget.classList.add('dragging');
}

function handleDragEnd(e) {
    dragging = false;
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
        
        if (type !== 'note') return;
        
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
            showMessage('노트 이동 성공');
            fetchTreeData();
        } else {
            showMessage(result.message || '이동 실패');
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류 발생');
    }
}

async function handleFileDrop(e, targetFolderId) {
    e.preventDefault();
    e.currentTarget.classList.remove('drop-target');
    
    try {
        const data = JSON.parse(e.dataTransfer.getData('text/plain'));
        const { item, type } = data;
        
        if (type !== 'file') return;
        
        const formData = new FormData();
        formData.append('fileId', item.id || item.gridfsId);
        if (targetFolderId) formData.append('targetFolderId', targetFolderId);
        
        const res = await secureFetch('/api/folders/move-file', {
            method: 'PUT',
            headers: new Headers({ [csrfHeader]: csrfToken }),
            body: formData
        });
        
        const result = await res.json();
        if (result.success) {
            showMessage('파일 이동 성공');
            fetchTreeData();
        } else {
            showMessage(result.message || '이동 실패');
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류 발생');
    }
}

// ========== 27. 검색 기능 ==========
async function performSearch(keyword) {
    if (!keyword || keyword.length < 2) {
        showMessage('검색어는 2글자 이상 입력해주세요.');
        return;
    }
    
    const results = [];
    
    if (currentTab === 'notes') {
        searchInNotes(itemsData.notes || [], keyword, [], results);
        searchInNoteFolders(itemsData.noteFolders || [], keyword, [], results);
    } else {
        searchInFiles(itemsData.files || [], keyword, [], results);
        searchInFileFolders(itemsData.fileFolders || [], keyword, [], results);
    }
    
    displaySearchResults(results, keyword);
}

function searchInNotes(notes, keyword, path, results) {
    notes.forEach(note => {
        const title = (note.title || '').toLowerCase();
        const content = (note.content || '').toLowerCase();
        
        if (title.includes(keyword) || content.includes(keyword)) {
            results.push({
                type: 'note',
                item: note,
                name: note.title || '(제목 없음)',
                path: path.join(' > '),
                icon: '📄'
            });
        }
    });
}

function searchInNoteFolders(folders, keyword, path, results) {
    folders.forEach(folder => {
        const folderName = (folder.folderName || '').toLowerCase();
        const currentPath = [...path, folder.folderName];
        
        if (folderName.includes(keyword)) {
            results.push({
                type: 'noteFolder',
                item: folder,
                name: folder.folderName,
                path: path.join(' > '),
                icon: '📁'
            });
        }
        
        if (folder.notes?.length) {
            searchInNotes(folder.notes, keyword, currentPath, results);
        }
        
        if (folder.subfolders?.length) {
            searchInNoteFolders(folder.subfolders, keyword, currentPath, results);
        }
    });
}

function searchInFiles(files, keyword, path, results) {
    files.forEach(file => {
        const fileName = (file.originalName || '').toLowerCase();
        
        if (fileName.includes(keyword)) {
            results.push({
                type: 'file',
                item: file,
                name: file.originalName || '(파일명 없음)',
                path: path.join(' > '),
                icon: getFileIcon(file.originalName || '')
            });
        }
    });
}

function searchInFileFolders(folders, keyword, path, results) {
    folders.forEach(folder => {
        const folderName = (folder.folderName || '').toLowerCase();
        const currentPath = [...path, folder.folderName];
        
        if (folderName.includes(keyword)) {
            results.push({
                type: 'folder',
                item: folder,
                name: folder.folderName,
                path: path.join(' > '),
                icon: '📁'
            });
        }
        
        if (folder.files?.length) {
            searchInFiles(folder.files, keyword, currentPath, results);
        }
        
        if (folder.subfolders?.length) {
            searchInFileFolders(folder.subfolders, keyword, currentPath, results);
        }
    });
}

function displaySearchResults(results, keyword) {
    const modal = document.getElementById('searchModal');
    const container = document.getElementById('searchResultsContainer');
    
    if (!modal || !container) return;
    
    container.innerHTML = '';
    
    if (results.length === 0) {
        container.innerHTML = `
            <div class="search-no-results">
                <p>"${escapeHtml(keyword)}"에 대한 검색 결과가 없습니다.</p>
            </div>
        `;
    } else {
        results.forEach(result => {
            const item = document.createElement('div');
            item.className = 'search-result-item';
            item.innerHTML = `
                <span class="search-result-icon">${result.icon}</span>
                <div class="search-result-info">
                    <div class="search-result-name">${escapeHtml(result.name)}</div>
                    ${result.path ? `<div class="search-result-path">${escapeHtml(result.path)}</div>` : ''}
                </div>
            `;
            
            item.addEventListener('click', () => {
                selectSearchResult(result);
                closeSearchModal();
            });
            
            container.appendChild(item);
        });
    }
    
    modal.style.display = 'flex';
}

function selectSearchResult(result) {
    setTimeout(() => {
        if (result.type === 'note') {
            const noteEl = document.querySelector(`[data-note-idx="${result.item.noteIdx}"]`);
            if (noteEl) {
                noteEl.click();
                noteEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        } else if (result.type === 'file') {
            const fileEl = document.querySelector(`[data-gridfs-id="${result.item.gridfsId}"]`);
            if (fileEl) {
                fileEl.click();
                fileEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        } else if (result.type === 'noteFolder' || result.type === 'folder') {
            const folderId = result.type === 'noteFolder' ? result.item.folderId : result.item.id;
            const folderEl = document.querySelector(`[data-folder-id="${folderId}"]`);
            if (folderEl) {
                folderEl.click();
                folderEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
    }, 100);
}

function closeSearchModal() {
    const modal = document.getElementById('searchModal');
    if (modal) modal.style.display = 'none';
}

// ========== 28. 헬퍼 함수 ==========
async function secureFetch(url, options = {}) {
    const headers = options.headers instanceof Headers ? options.headers : new Headers(options.headers || {});
    if (csrfToken) headers.set(csrfHeader, csrfToken);
    
    options.headers = headers;
    options.credentials = options.credentials || 'same-origin';
    options.cache = options.cache || 'no-store';
    
    return fetch(url, options);
}

function getFileExtension(filename) {
    if (!filename || !filename.includes('.')) return '';
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
}

function getFileIcon(filename) {
    const ext = getFileExtension(filename);
    const icons = {
        'pdf': '📕',
        'docx': '📘',
        'doc': '📘',
        'xlsx': '📗',
        'xls': '📗',
        'pptx': '📙',
        'ppt': '📙',
        'txt': '📄',
        'md': '📝',
        'jpg': '🖼️',
        'jpeg': '🖼️',
        'png': '🖼️',
        'gif': '🖼️',
        'csv': '📊',
        'json': '📋',
        'xml': '📋'
    };
    return icons[ext] || '📄';
}

function showMessage(message) {
    alert(message);
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
}

function clearContent() {
    hideAllViews();
    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const welcomeMsg = document.getElementById('welcomeMessage');
    const buttonContainer = document.getElementById('buttonContainer');
    
    if (titleEl) titleEl.textContent = '파일이나 노트를 선택해주세요';
    if (contentEl) {
        contentEl.value = '';
        contentEl.style.display = 'none';
    }
    if (welcomeMsg) welcomeMsg.style.display = 'block';
    if (buttonContainer) buttonContainer.innerHTML = '';
}
