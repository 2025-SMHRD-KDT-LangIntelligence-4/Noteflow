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
let isViewerMode = false;
// ========== 3. 초기화 ==========
document.addEventListener('DOMContentLoaded', () => {
    setupTabs();
    setupSearch();
    setupFileInput();
    setupCreateFolder();
    fetchTreeData();
    setupRootDropZone();
    setupGlobalDnDDelegation();

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
    const clearBtn = document.getElementById('clearSearchBtn');

    if (!input) return;

    let searchTimeout;

    // ✅ 실시간 검색 (300ms 디바운스)
    input.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);

        const keyword = e.target.value.trim().toLowerCase();

        // 검색어가 있으면 X 버튼 표시
        if (keyword) {
            clearBtn.style.display = 'inline-block';
        } else {
            clearBtn.style.display = 'none';
        }

        searchTimeout = setTimeout(() => {
            filterTree(keyword);
        }, 300);
    });

    // ✅ Enter 키로 검색
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            const keyword = input.value.trim().toLowerCase();
            filterTree(keyword);
        }
    });

    // ✅ X 버튼 클릭 시 검색 초기화
    clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.style.display = 'none';
        filterTree('');  // 전체 표시
    });
}

// 필터링
function filterTree(keyword) {
    const listEl = document.getElementById('itemList');
    if (!listEl) return;

    console.log('검색 키워드:', keyword);

    // 검색어가 없으면 전체 표시
    if (!keyword) {
        document.querySelectorAll('.note-item, .file-item, .folder-item, .folder-container').forEach(el => {
            el.style.display = '';
        });

        // 폴더 접기 상태 초기화
        document.querySelectorAll('.folder-children').forEach(el => {
            el.classList.remove('expanded');
        });
        document.querySelectorAll('.folder-toggle').forEach(el => {
            el.classList.remove('expanded');
            el.textContent = '▶';
        });

        return;
    }

    let matchCount = 0;

    // ========== 노트 필터링 ==========
    if (currentTab === 'notes') {
        // 모든 항목 숨기기
        document.querySelectorAll('.note-item, .folder-container').forEach(el => {
            el.style.display = 'none';
        });

        // 노트 검색
        document.querySelectorAll('.note-item').forEach(el => {
            const title = el.querySelector('.note-title')?.textContent.toLowerCase() || '';

            if (title.includes(keyword)) {
                el.style.display = '';
                matchCount++;

                // 부모 폴더들도 표시
                showParentFolders(el);
            }
        });

        // 폴더 검색
        document.querySelectorAll('.folder-item').forEach(el => {
            const folderName = el.querySelector('.folder-name')?.textContent.toLowerCase() || '';

            if (folderName.includes(keyword)) {
                const container = el.closest('.folder-container');
                if (container) {
                    container.style.display = '';
                    matchCount++;

                    // 하위 항목도 모두 표시
                    const children = container.querySelector('.folder-children');
                    if (children) {
                        children.classList.add('expanded');
                        children.style.display = 'block';
                        children.querySelectorAll('.note-item, .folder-container').forEach(child => {
                            child.style.display = '';
                        });
                    }

                    // 토글 버튼 확장
                    const toggle = el.querySelector('.folder-toggle');
                    if (toggle) {
                        toggle.classList.add('expanded');
                        toggle.textContent = '▼';
                    }
                }
            }
        });
    }

    // ========== 파일 필터링 ==========
    else if (currentTab === 'files') {
        // 모든 항목 숨기기
        document.querySelectorAll('.file-item, .folder-container').forEach(el => {
            el.style.display = 'none';
        });

        // 파일 검색
        document.querySelectorAll('.file-item').forEach(el => {
            const fileName = el.querySelector('.file-name')?.textContent.toLowerCase() || '';

            if (fileName.includes(keyword)) {
                el.style.display = '';
                matchCount++;

                // 부모 폴더들도 표시
                showParentFolders(el);
            }
        });

        // 폴더 검색
        document.querySelectorAll('.folder-item').forEach(el => {
            const folderName = el.querySelector('.folder-name')?.textContent.toLowerCase() || '';

            if (folderName.includes(keyword)) {
                const container = el.closest('.folder-container');
                if (container) {
                    container.style.display = '';
                    matchCount++;

                    // 하위 항목도 모두 표시
                    const children = container.querySelector('.folder-children');
                    if (children) {
                        children.classList.add('expanded');
                        children.style.display = 'block';
                        children.querySelectorAll('.file-item, .folder-container').forEach(child => {
                            child.style.display = '';
                        });
                    }

                    // 토글 버튼 확장
                    const toggle = el.querySelector('.folder-toggle');
                    if (toggle) {
                        toggle.classList.add('expanded');
                        toggle.textContent = '▼';
                    }
                }
            }
        });
    }

    // ✅ 검색 결과 메시지
    console.log(`검색 결과: ${matchCount}개 발견`);

    if (matchCount === 0) {
        const listEl = document.getElementById('itemList');
        if (listEl) {
            listEl.innerHTML = `<p class="empty-message">🔍 "${keyword}"에 대한 검색 결과가 없습니다.</p>`;
        }
    }
}

// 부모 폴더들을 모두 표시하는 헬퍼 함수
function showParentFolders(element) {
    let parent = element.parentElement;

    while (parent && parent.id !== 'itemList') {
        if (parent.classList.contains('folder-container')) {
            parent.style.display = '';

            // 폴더 확장
            const children = parent.querySelector('.folder-children');
            if (children) {
                children.classList.add('expanded');
                children.style.display = 'block';
            }

            // 토글 버튼 확장
            const toggle = parent.querySelector('.folder-toggle');
            if (toggle) {
                toggle.classList.add('expanded');
                toggle.textContent = '▼';
            }
        }

        parent = parent.parentElement;
    }
}

// ========== 6. 파일 업로드 설정 ==========
function setupFileInput() {
    const fileInput = document.getElementById('fileInput');
    const uploadBtn = document.getElementById('uploadBtn');

    if (!uploadBtn || !fileInput) return;

    // ✅ 탭 전환 시 업로드 버튼 표시/숨김
    function updateUploadButton() {
        if (currentTab === 'files') {
            uploadBtn.style.display = 'inline-block';  // 원본파일 탭만 표시
        } else {
            uploadBtn.style.display = 'none';  // 요약본 탭은 숨김
        }
    }

    // 초기 실행
    updateUploadButton();

    // 탭 전환 시 업데이트
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.addEventListener('click', () => {
            setTimeout(updateUploadButton, 0);  // 탭 전환 후 실행
        });
    });

    uploadBtn.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        // ✅ 원본파일 탭에서만 업로드 가능
        if (currentTab !== 'files') {
            showMessage('파일은 원본파일 탭에서만 업로드할 수 있습니다.');
            fileInput.value = '';
            return;
        }

        const form = new FormData();
        form.append('file', file);

        // 선택된 폴더가 있으면 folderId 추가
        if (selectedItemType === 'folder' && selectedItem?.id) {
            form.append('folderId', selectedItem.id);
        }

        try {
            const res = await fetch('/api/files/upload', {
                method: 'POST',
                headers: {[csrfHeader]: csrfToken},
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

    // ✅ 폴더 드래그 가능하게
    folderItem.draggable = true;

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'item-checkbox';
    checkbox.style.marginRight = '8px';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';
    checkbox.addEventListener('click', e => e.stopPropagation());
    checkbox.addEventListener('change', e => {
        e.stopPropagation();
        if (e.target.checked) {
            toggleMultiFileSelection({item: folder, el: folderItem, type: 'noteFolder'});
        } else {
            const idx = selectedItems.findIndex(si => si.item.folderId === folder.folderId);
            if (idx !== -1) {
                selectedItems.splice(idx, 1);
                folderItem.classList.remove('multi-selected');
            }
        }
        updateMultiSelectionUI();
    });

    const hasChildren = (folder.notes?.length > 0) || (folder.subfolders?.length > 0);

    const toggle = document.createElement('span');
    toggle.className = hasChildren ? 'folder-toggle' : 'empty';
    toggle.innerHTML = hasChildren ? '▶' : '';
    toggle.addEventListener('click', e => {
        e.stopPropagation();
        toggleFolder(container, toggle);
    });

    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📁';

    const name = document.createElement('span');
    name.className = 'folder-name';
    name.textContent = folder.folderName;

    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); renameNoteFolderPrompt(${folder.folderId})" title="이름 변경">✏️</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteNoteFolder(${folder.folderId})" title="삭제">🗑️</button>
    `;

    folderItem.appendChild(checkbox);
    folderItem.appendChild(toggle);
    folderItem.appendChild(icon);
    folderItem.appendChild(name);
    folderItem.appendChild(actions);

    // 클릭 이벤트
    folderItem.addEventListener('click', e => {
        if (dragging) return;
        e.stopPropagation();

        const multi = e.ctrlKey || e.metaKey;
        if (multi && currentTab === 'notes') {
            toggleMultiFileSelection({item: folder, el: folderItem, type: 'noteFolder'});
            checkbox.checked = selectedItems.some(si => si.item.folderId === folder.folderId);
        } else {
            clearMultiSelection();
            selectFolder(folder, folderItem, 'noteFolder');
        }
    });

    // ✅ 드래그 이벤트 추가
    folderItem.addEventListener('dragstart', e => handleDragStart(e, folder, 'noteFolder'));
    folderItem.addEventListener('dragend', handleDragEnd);

    // 드롭 이벤트 (폴더 안에 드롭 가능)
    folderItem.addEventListener('dragover', handleDragOver);
    folderItem.addEventListener('drop', (e) => handleNoteFolderDrop(e, folder.folderId));
    folderItem.addEventListener('dragleave', handleDragLeave);

    container.appendChild(folderItem);

    if (hasChildren) {
        const children = document.createElement('div');
        children.className = 'folder-children expanded';
        children.dataset.expanded = 'true';

        if (folder.subfolders?.length) {
            folder.subfolders.forEach(sub => {
                children.appendChild(createNoteFolderTreeElement(sub, depth + 1));
            });
        }

        if (folder.notes?.length) {
            folder.notes.forEach(note => {
                children.appendChild(createNoteElement(note, depth + 1));
            });
        }

        container.appendChild(children);
    }

    return container;
}
// ========== 노트 폴더에 드롭 (노트 또는 노트 폴더) ==========
async function handleNoteFolderDrop(e, targetFolderId) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('drop-target');

    try {
        const dataStr = e.dataTransfer.getData('text/plain');
        if (!dataStr) return;

        const {item, type} = JSON.parse(dataStr);

        // 노트 이동
        if (type === 'note') {
            const formData = new FormData();
            formData.append('noteId', item.noteIdx);
            formData.append('targetFolderId', targetFolderId);

            const res = await secureFetch('/api/unified/notes/move', {
                method: 'PUT',
                headers: new Headers({[csrfHeader]: csrfToken}),
                body: formData
            });

            const result = await res.json();
            if (result.success) {
                showMessage('노트를 이동했습니다.');
                fetchTreeData();
            }
        }
        // ✅ 노트 폴더 이동
        else if (type === 'noteFolder') {
            if (item.folderId === targetFolderId) {
                showMessage('같은 폴더입니다.');
                return;
            }

            const res = await secureFetch(`/api/unified/note-folders/${item.folderId}/move`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({targetFolderId})
            });

            const result = await res.json();
            if (result.success) {
                showMessage('폴더를 이동했습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message || '이동 실패');
            }
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류 발생');
    }
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

    // ✅ 폴더 드래그 가능하게
    folderItem.draggable = true;

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'item-checkbox';
    checkbox.style.marginRight = '8px';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';
    checkbox.addEventListener('click', e => e.stopPropagation());
    checkbox.addEventListener('change', e => {
        e.stopPropagation();
        if (e.target.checked) {
            toggleMultiFileSelection({item: folder, el: folderItem, type: 'folder'});
        } else {
            const idx = selectedItems.findIndex(si => si.item.id === folder.id);
            if (idx !== -1) {
                selectedItems.splice(idx, 1);
                folderItem.classList.remove('multi-selected');
            }
        }
        updateMultiSelectionUI();
    });

    const hasChildren = (folder.files?.length > 0) || (folder.subfolders?.length > 0);

    const toggle = document.createElement('span');
    toggle.className = hasChildren ? 'folder-toggle' : 'empty';
    toggle.innerHTML = hasChildren ? '▶' : '';
    toggle.addEventListener('click', e => {
        e.stopPropagation();
        toggleFolder(container, toggle);
    });

    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📁';

    const name = document.createElement('span');
    name.className = 'folder-name';
    name.textContent = folder.folderName;

    const actions = document.createElement('div');
    actions.className = 'item-actions';
    actions.innerHTML = `
        <button class="action-icon-btn" onclick="event.stopPropagation(); downloadFolder('${folder.id}')" title="다운로드">💾</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); renameFolderPrompt('${folder.id}')" title="이름 변경">✏️</button>
        <button class="action-icon-btn" onclick="event.stopPropagation(); deleteFolderPrompt('${folder.id}')" title="삭제">🗑️</button>
    `;

    folderItem.appendChild(checkbox);
    folderItem.appendChild(toggle);
    folderItem.appendChild(icon);
    folderItem.appendChild(name);
    folderItem.appendChild(actions);

    // 클릭 이벤트
    folderItem.addEventListener('click', e => {
        if (dragging) return;
        e.stopPropagation();

        const multi = e.ctrlKey || e.metaKey;
        if (multi && currentTab === 'files') {
            toggleMultiFileSelection({item: folder, el: folderItem, type: 'folder'});
            checkbox.checked = selectedItems.some(si => si.item.id === folder.id);
        } else {
            clearMultiSelection();
            selectFolder(folder, folderItem, 'folder');
        }
    });

    // ✅ 드래그 이벤트 추가
    folderItem.addEventListener('dragstart', e => handleDragStart(e, folder, 'folder'));
    folderItem.addEventListener('dragend', handleDragEnd);

    // 드롭 이벤트 (폴더 안에 드롭 가능)
    folderItem.addEventListener('dragover', handleDragOver);
    folderItem.addEventListener('drop', (e) => handleFileFolderDrop(e, folder.id));
    folderItem.addEventListener('dragleave', handleDragLeave);

    container.appendChild(folderItem);

    if (hasChildren) {
        const children = document.createElement('div');
        children.className = 'folder-children expanded';
        children.dataset.expanded = 'true';

        if (folder.subfolders?.length) {
            folder.subfolders.forEach(sub => {
                children.appendChild(createFileFolderTreeElement(sub, depth + 1));
            });
        }

        if (folder.files?.length) {
            folder.files.forEach(file => {
                children.appendChild(createFileElement(file, depth + 1));
            });
        }

        container.appendChild(children);
    }

    return container;
}
// ========== 파일 폴더에 드롭 (파일 또는 파일 폴더) ==========
async function handleFileFolderDrop(e, targetFolderId) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('drop-target');

    try {
        const dataStr = e.dataTransfer.getData('text/plain');
        if (!dataStr) return;

        const {item, type} = JSON.parse(dataStr);

        // 파일 이동
        if (type === 'file') {
            const formData = new FormData();
            formData.append('fileId', item.id || item.gridfsId);
            if (targetFolderId) {
                formData.append('targetFolderId', targetFolderId);
            }

            const res = await secureFetch('/api/folders/move-file', {
                method: 'PUT',
                headers: new Headers({[csrfHeader]: csrfToken}),
                body: formData
            });

            const result = await res.json();
            if (result.success) {
                showMessage('파일을 이동했습니다.');
                fetchTreeData();
            }
        }
        // ✅ 파일 폴더 이동
        else if (type === 'folder') {
            if (item.id === targetFolderId) {
                showMessage('같은 폴더입니다.');
                return;
            }

            const res = await secureFetch(`/api/folders/${item.id}/move`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({targetFolderId})
            });

            const result = await res.json();
            if (result.success) {
                showMessage('폴더를 이동했습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message || '이동 실패');
            }
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류 발생');
    }
}

// ========== 다중 선택 관리 개선 ==========
function addToMultiSelection(item) {
    const exists = selectedItems.find(si =>
        si.item.gridfsId === item.item.gridfsId ||
        si.item.noteIdx === item.item.noteIdx
    );

    if (!exists) {
        selectedItems.push(item);
        item.el.classList.add('multi-selected');
    }
}

function removeFromMultiSelection(id) {
    selectedItems = selectedItems.filter(si =>
        si.item.gridfsId !== id && si.item.noteIdx !== id
    );
    document.querySelectorAll('.multi-selected').forEach(el => {
        el.classList.remove('multi-selected');
    });
}

function clearMultiSelection() {
    selectedItems = [];

    document.querySelectorAll('.multi-selected').forEach(el => {
        el.classList.remove('multi-selected');
    });

    document.querySelectorAll('.item-checkbox').forEach(cb => {
        cb.checked = false;
    });

    // ✅ 버튼 컨테이너 초기화
    updateMultiSelectionUI();
}

function updateMultiSelectionButtons() {
    if (selectedItems.length > 0) {
        updateButtons('multi');
    }
}

// ========== 12. 노트 요소 생성 ==========
function createNoteElement(note, depth) {
    const div = document.createElement('div');
    div.className = 'note-item';
    div.draggable = true;  // ✅ 드래그 가능
    div.style.paddingLeft = `${depth * 20 + 30}px`;

    // 체크박스
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'item-checkbox';
    checkbox.style.marginRight = '8px';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';

    checkbox.addEventListener('click', (e) => {
        e.stopPropagation();
    });

    checkbox.addEventListener('change', (e) => {
        e.stopPropagation();
        if (e.target.checked) {
            toggleMultiFileSelection({
                item: note,
                el: div,
                type: 'note'
            });
        } else {
            const idx = selectedItems.findIndex(si =>
                si.item.noteIdx === note.noteIdx
            );
            if (idx !== -1) {
                selectedItems.splice(idx, 1);
            }
            div.classList.remove('multi-selected');
        }
        updateMultiSelectionUI();
    });

    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📝';

    const title = document.createElement('span');
    title.className = 'note-title';
    title.textContent = note.title || '(제목없음)';

    div.appendChild(checkbox);
    div.appendChild(icon);
    div.appendChild(title);

    div.addEventListener('click', (e) => {
        if (dragging) return;  // ✅ 드래그 중에는 선택 안되게
        e.stopPropagation();

        const multi = e.ctrlKey || e.metaKey;
        if (multi && currentTab === 'notes') {
            toggleMultiFileSelection({
                item: note,
                el: div,
                type: 'note'
            });
            checkbox.checked = selectedItems.some(si =>
                si.item.noteIdx === note.noteIdx
            );
        } else {
            clearMultiSelection();
            selectNote(note, div);
        }
    });

    // ✅ 드래그 이벤트 추가
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

    // ✅ 체크박스 추가 (기존 아이콘 앞에)
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'item-checkbox';
    checkbox.style.marginRight = '8px';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';

    // ✅ 체크박스 이벤트 (이벤트 전파 차단)
    checkbox.addEventListener('click', (e) => {
        e.stopPropagation();
    });

    checkbox.addEventListener('change', (e) => {
        e.stopPropagation();
        if (e.target.checked) {
            toggleMultiFileSelection({ item: file, el: div , type:'file'});
        } else {
            // 체크 해제
            const idx = selectedItems.findIndex(si =>
                si.item.gridfsId === file.gridfsId
            );
            if (idx !== -1) {
                selectedItems.splice(idx, 1);
            }
            div.classList.remove('multi-selected');
        }
        updateMultiSelectionUI();
    });

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

    div.appendChild(checkbox);  // ✅ 체크박스 먼저
    div.appendChild(icon);
    div.appendChild(name);
    div.appendChild(actions);

    // ✅ 기존 클릭 이벤트 유지
    div.addEventListener('click', (e) => {
        if (dragging) return;
        e.stopPropagation();

        const multi = e.ctrlKey || e.metaKey;
        if (multi && currentTab === 'files') {
            toggleMultiFileSelection({ item: file, el: div });
            // ✅ 체크박스 상태 동기화
            checkbox.checked = selectedItems.some(si =>
                si.item.gridfsId === file.gridfsId
            );
        } else {
            clearMultiSelection();
            selectFile(file, div);
        }
    });

    // ✅ 기존 드래그 이벤트 유지
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
    clearMultiSelection();  // ✅ 추가
    selectedItem = note;
    selectedItemType = 'note';
    updateSelectedState(el);
    showNoteContent(note);
}



// ========== updateMultiSelectionUI 함수 추가 ==========
function updateMultiSelectionUI() {
    const container = document.getElementById('buttonContainer');
    if (!container) return;

    if (selectedItems.length > 0) {
        // 선택된 항목들 하이라이트
        selectedItems.forEach(si => {
            si.el.classList.add('multi-selected');
            const cb = si.el.querySelector('.item-checkbox');
            if (cb) cb.checked = true;
        });

        // ✅ 다중 선택 버튼 표시
        container.innerHTML = `
            <button class="btn-download" onclick="downloadSelectedAsZip()">📦 선택 항목 ZIP (${selectedItems.length}개)</button>
            <button class="btn-cancel" onclick="clearMultiSelection()">❌ 선택 해제</button>
        `;
    } else {
        // 모든 하이라이트 제거
        document.querySelectorAll('.multi-selected').forEach(el => {
            el.classList.remove('multi-selected');
        });

        // 모든 체크박스 해제
        document.querySelectorAll('.item-checkbox').forEach(cb => {
            cb.checked = false;
        });

        // ✅ 버튼 초기화 (선택된 항목이 있으면 그 버튼 표시)
        if (selectedItem) {
            updateButtons(selectedItemType);
        } else {
            container.innerHTML = '';
        }
    }
}

function selectFile(file, el) {
    clearMultiSelection();
    selectedItem = file;
    selectedItemType = 'file';
    updateSelectedState(el);
    showFileContent(file);
}

function selectFolder(folder, el, type) {
    clearMultiSelection();  // ✅ 추가
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

    if (toastEditor) {
        toastEditor.destroy();
        toastEditor = null;
    }

    document.getElementById('editorArea').style.display = 'none';
    document.getElementById('itemTitle').textContent = '';
    document.getElementById('itemContent').value = '';
    document.getElementById('buttonContainer').innerHTML = '';
    document.getElementById('welcomeMessage').style.display = 'block';
}
// ========== 16. 다중 선택 ==========
function toggleMultiFileSelection(item) {
    // ✅ item에 type이 있는지 확인
    if (!item.type) {
        console.warn('⚠️ toggleMultiFileSelection: type이 없음', item);
        // type 추론
        if (item.item.gridfsId) {
            item.type = 'file';
        } else if (item.item.noteIdx) {
            item.type = 'note';
        } else if (item.item.folderId) {
            item.type = 'folder';
        }
    }

    const idx = selectedItems.findIndex(si => {
        if (item.type === 'file') {
            return si.item.gridfsId === item.item.gridfsId;
        } else if (item.type === 'note') {
            return si.item.noteIdx === item.item.noteIdx;
        }
        return false;
    });

    if (idx !== -1) {
        selectedItems.splice(idx, 1);
        item.el.classList.remove('multi-selected');
        const cb = item.el.querySelector('.item-checkbox');
        if (cb) cb.checked = false;
    } else {
        selectedItems.push(item);
        item.el.classList.add('multi-selected');
        const cb = item.el.querySelector('.item-checkbox');
        if (cb) cb.checked = true;
    }

    updateMultiSelectionUI();
}

// ========== 17. 컨텐츠 표시 ==========
let toastEditor = null;

// 노트 내용 표시 시 Toast Editor 사용
function showNoteContent(note) {
    hideAllViews();

    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const editorArea = document.getElementById('editorArea');
    const welcomeMsg = document.getElementById('welcomeMessage');

    if (welcomeMsg) welcomeMsg.style.display = 'none';

    titleEl.textContent = note.title || '제목없음';
    titleEl.contentEditable = 'false';

    // ✅ 내용을 변수에 저장
    originalContent = note.content || '';

    if (editorArea) {
        editorArea.style.display = 'block';
        if (contentEl) contentEl.style.display = 'none';

        if (toastEditor) {
            toastEditor.destroy();
            toastEditor = null;
        }

        setTimeout(() => {
            try {
                // ✅ Viewer 모드로 생성
                toastEditor = toastui.Editor.factory({
                    el: editorArea,
                    height: '70vh',
                    viewer: true,
                    initialValue: originalContent
                });

                isViewerMode = true;  // ✅ 플래그 설정
                console.log('Toast Editor Viewer 생성 완료');
                updateButtons('note');

            } catch (e) {
                console.error('Toast Editor 생성 실패:', e);
                if (contentEl) {
                    editorArea.style.display = 'none';
                    contentEl.style.display = 'block';
                    contentEl.value = originalContent;
                    contentEl.readOnly = true;
                    contentEl.classList.add('readonly');
                }
                updateButtons('note');
            }
        }, 100);
    }
}

async function showFileContent(file) {
    hideAllViews();

    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const previewArea = document.getElementById('previewArea');
    const pdfPreview = document.getElementById('pdfPreview');
    const imagePreview = document.getElementById('imagePreview');
    const editorArea = document.getElementById('editorArea');
    const spreadsheetArea = document.getElementById('spreadsheetArea');
    const welcomeMsg = document.getElementById('welcomeMessage');

    if (welcomeMsg) welcomeMsg.style.display = 'none';

    titleEl.textContent = file.originalName || file.storedName;

    const fileName = (file.originalName || '').toLowerCase();
    const ext = fileName.split('.').pop();

    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'];
    const textExts = ['txt', 'md', 'log', 'json', 'xml', 'html', 'css', 'js', 'java', 'py', 'sql', 'bat', 'sh'];

    try {
        // PDF
        if (ext === 'pdf') {
            previewArea.style.display = 'block';
            pdfPreview.style.display = 'block';
            imagePreview.style.display = 'none';
            pdfPreview.src = `/api/files/preview/${file.gridfsId}`;
        }

        // 이미지
        else if (imageExts.includes(ext)) {
            previewArea.style.display = 'block';
            pdfPreview.style.display = 'none';
            imagePreview.style.display = 'block';
            imagePreview.src = `/api/files/download/${file.gridfsId}`;
        }

        // Excel/CSV
        else if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') {
            spreadsheetArea.style.display = 'block';

            const res = await secureFetch(`/api/files/preview/${file.gridfsId}`);

            if (ext === 'csv') {
                const text = await res.text();
                const lines = text.split('\n').filter(line => line.trim());
                let html = '<table class="csv-table"><tbody>';

                lines.forEach((line, idx) => {
                    const cells = line.split(',').map(cell => cell.trim());
                    const tag = idx === 0 ? 'th' : 'td';
                    html += '<tr>';
                    cells.forEach(cell => {
                        html += `<${tag}>${escapeHtml(cell)}</${tag}>`;
                    });
                    html += '</tr>';
                });

                html += '</tbody></table>';
                spreadsheetArea.innerHTML = html;

            } else {
                const arrayBuffer = await res.arrayBuffer();

                if (typeof XLSX === 'undefined') {
                    contentEl.style.display = 'block';
                    contentEl.value = 'Excel 파일을 표시하려면 SheetJS 라이브러리가 필요합니다.';
                    return;
                }

                const workbook = XLSX.read(arrayBuffer, { type: 'array' });

                // ✅ 시트 탭 생성
                let html = '<div class="excel-container">';
                html += '<div class="sheet-tabs">';
                workbook.SheetNames.forEach((sheetName, idx) => {
                    html += `<button class="sheet-tab ${idx === 0 ? 'active' : ''}" data-sheet-idx="${idx}">${escapeHtml(sheetName)}</button>`;
                });
                html += '</div>';

                // ✅ 시트 내용 (초기에는 첫 번째 시트)
                html += '<div class="sheet-content">';
                const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
                html += XLSX.utils.sheet_to_html(firstSheet);
                html += '</div></div>';

                spreadsheetArea.innerHTML = html;

                // ✅ 탭 클릭 이벤트 추가
                setTimeout(() => {
                    const tabs = spreadsheetArea.querySelectorAll('.sheet-tab');
                    tabs.forEach(tab => {
                        tab.addEventListener('click', (e) => {
                            const sheetIdx = parseInt(e.target.dataset.sheetIdx);

                            // 활성 탭 변경
                            tabs.forEach(t => t.classList.remove('active'));
                            e.target.classList.add('active');

                            // 시트 내용 변경
                            const sheetName = workbook.SheetNames[sheetIdx];
                            const sheet = workbook.Sheets[sheetName];
                            const sheetContent = spreadsheetArea.querySelector('.sheet-content');
                            sheetContent.innerHTML = XLSX.utils.sheet_to_html(sheet);

                            console.log(`Sheet 전환: ${sheetName}`);
                        });
                    });
                }, 0);
            }
        }

        // ✅ 모든 텍스트 파일 → Toast Viewer
        else if (textExts.includes(ext) || ext === 'docx' || ext === 'hwp') {
            const res = await secureFetch(`/api/files/preview/${file.gridfsId}`);
            const text = await res.text();

            // 내용을 변수에 저장 (편집용)
            originalContent = text;

            editorArea.style.display = 'block';

            if (toastEditor) {
                toastEditor.destroy();
                toastEditor = null;
            }

            toastEditor = toastui.Editor.factory({
                el: editorArea,
                height: '70vh',
                viewer: true,
                initialValue: text
            });

            isViewerMode = true;
            console.log(`${ext.toUpperCase()} 파일 Toast Viewer로 표시`);
        }

        // 기타
        else {
            contentEl.style.display = 'block';
            contentEl.value = `파일명: ${file.originalName}\n확장자: ${ext}\n\n이 파일 형식은 미리보기를 지원하지 않습니다.`;
            contentEl.readOnly = true;
        }

    } catch (e) {
        console.error('파일 로드 오류:', e);
        contentEl.style.display = 'block';
        contentEl.value = `파일 로드 중 오류 발생:\n${e.message}`;
        contentEl.readOnly = true;
    }

    updateButtons('file');
}


function enterEditModeForFile() {
    if (!selectedItem || selectedItemType !== 'file') return;

    const titleEl = document.getElementById('itemTitle');
    const editorArea = document.getElementById('editorArea');

    originalTitle = titleEl.textContent;

    // ✅ Viewer를 Editor로 전환
    if (toastEditor && isViewerMode) {
        const content = originalContent;  // 저장된 원본 사용
        toastEditor.destroy();

        toastEditor = new toastui.Editor({
            el: editorArea,
            height: '70vh',
            initialEditType: 'wysiwyg',
            initialValue: content,
            previewStyle: 'vertical',
            hideModeSwitch: false,
            toolbarItems: [
                ['heading', 'bold', 'italic', 'strike'],
                ['hr', 'quote'],
                ['ul', 'ol', 'task', 'indent', 'outdent'],
                ['table', 'link'],
                ['code', 'codeblock']
            ]
        });

        isViewerMode = false;
        console.log('✅ 파일 편집 모드 활성화');
    }

    titleEl.contentEditable = 'false';  // 파일명은 편집 불가

    const container = document.getElementById('buttonContainer');
    if (container) {
        container.innerHTML = `
            <button class="btn-save" id="saveFileBtn">💾 저장</button>
            <button class="btn-cancel" id="cancelFileBtn">❌ 취소</button>
        `;

        setTimeout(() => {
            document.getElementById('saveFileBtn')?.addEventListener('click', saveFile);
            document.getElementById('cancelFileBtn')?.addEventListener('click', cancelFileEdit);
        }, 0);
    }
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

    // ✅ selectedItemType 사용 (noteFolder 구분)
    updateButtons(selectedItemType);
}

// ========== 18. 모든 뷰 숨김 ==========
function hideAllViews() {
    const contentEl = document.getElementById('itemContent');
    const editorArea = document.getElementById('editorArea');
    const previewArea = document.getElementById('previewArea');
    const spreadsheetArea = document.getElementById('spreadsheetArea');

    if (contentEl) contentEl.style.display = 'none';
    if (editorArea) editorArea.style.display = 'none';  // ✅ 추가
    if (previewArea) previewArea.style.display = 'none';
    if (spreadsheetArea) spreadsheetArea.style.display = 'none';
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
            <button class="btn-edit" id="editBtn">✏️ 편집</button>
            <button class="btn-download" id="downloadBtn">⬇️ 다운로드</button>
            <button class="btn-delete" id="deleteBtn">🗑️ 삭제</button>
        `;

        // ✅ 이벤트 리스너로 연결 (더 안정적)
        setTimeout(() => {
            const editBtn = document.getElementById('editBtn');
            const downloadBtn = document.getElementById('downloadBtn');
            const deleteBtn = document.getElementById('deleteBtn');

            if (editBtn) {
                editBtn.addEventListener('click', enterEditMode);
                console.log('편집 버튼 이벤트 리스너 연결됨');
            }
            if (downloadBtn) {
                downloadBtn.addEventListener('click', downloadNote);
            }
            if (deleteBtn) {
                deleteBtn.addEventListener('click', deleteNotePrompt);
            }
        }, 0);

    } else if (type === 'file') {
        const fileName = selectedItem?.originalName || '';
        const isEditable = fileName.endsWith('.md') || fileName.endsWith('.txt');

        container.innerHTML = `
            ${isEditable ? '<button class="btn-edit" id="editFileBtn">✏️ 편집</button>' : ''}
            <button class="btn-download" id="downloadFileBtn">⬇️ 다운로드</button>
            <button class="btn-delete" id="deleteFileBtn">🗑️ 삭제</button>
        `;

        setTimeout(() => {
            const editBtn = document.getElementById('editFileBtn');
            const downloadBtn = document.getElementById('downloadFileBtn');
            const deleteBtn = document.getElementById('deleteFileBtn');

            if (editBtn) editBtn.addEventListener('click', enterEditModeForFile);
            if (downloadBtn) {
                downloadBtn.addEventListener('click', () => downloadSingleFile(selectedItem.gridfsId));
            }
            if (deleteBtn) {
                deleteBtn.addEventListener('click', () => deleteFilePrompt(selectedItem.gridfsId));
            }
        }, 0);

    } else if (type === 'folder' || type === 'noteFolder') {
        container.innerHTML = `
            <button class="btn-download" id="downloadFolderBtn">📦 ZIP 다운로드</button>
            <button class="btn-delete" id="deleteFolderBtn">🗑️ 폴더 삭제</button>
        `;

        setTimeout(() => {
            const downloadBtn = document.getElementById('downloadFolderBtn');
            const deleteBtn = document.getElementById('deleteFolderBtn');

            if (downloadBtn) downloadBtn.addEventListener('click', downloadFolderAsZip);
            if (deleteBtn) deleteBtn.addEventListener('click', deleteFolderPrompt);
        }, 0);

    } else if (type === 'multi') {
        container.innerHTML = `
            <button class="btn-download" id="downloadMultiBtn">📦 선택 항목 ZIP (${selectedItems.length}개)</button>
            <button class="btn-cancel" id="clearMultiBtn">❌ 선택 해제</button>
        `;

        setTimeout(() => {
            const downloadBtn = document.getElementById('downloadMultiBtn');
            const clearBtn = document.getElementById('clearMultiBtn');

            if (downloadBtn) downloadBtn.addEventListener('click', downloadSelectedAsZip);
            if (clearBtn) clearBtn.addEventListener('click', clearMultiSelection);
        }, 0);
    }
}
// ========== 21. 편집 모드 ==========

// ✅ Toast Editor 비활성화
function disableToastEditor() {
    if (!toastEditor) return;

    // 약간의 딜레이 후 실행 (DOM 완전 로드 대기)
    setTimeout(() => {
        // 1. ProseMirror 에디터 비활성화
        const editorEl = document.querySelector('.toastui-editor .ProseMirror');
        if (editorEl) {
            editorEl.setAttribute('contenteditable', 'false');
            editorEl.style.cursor = 'default';
            editorEl.style.backgroundColor = '#f8f9fa';

            // 모든 입력 이벤트 차단
            editorEl.addEventListener('keydown', preventEdit, true);
            editorEl.addEventListener('paste', preventEdit, true);
            editorEl.addEventListener('drop', preventEdit, true);
            editorEl.addEventListener('cut', preventEdit, true);
        }

        // 2. 툴바 완전 비활성화
        const toolbar = document.querySelector('.toastui-editor-toolbar');
        if (toolbar) {
            toolbar.style.pointerEvents = 'none';
            toolbar.style.opacity = '0.5';

            // 툴바 버튼 비활성화
            toolbar.querySelectorAll('button').forEach(btn => {
                btn.disabled = true;
                btn.style.cursor = 'not-allowed';
            });
        }

        console.log('✅ Toast Editor 읽기 전용 완료');
    }, 200);
}

// ✅ Toast Editor 활성화
function enableToastEditor() {
    if (!toastEditor) return;

    setTimeout(() => {
        // 1. ProseMirror 에디터 활성화
        const editorEl = document.querySelector('.toastui-editor .ProseMirror');
        if (editorEl) {
            editorEl.setAttribute('contenteditable', 'true');
            editorEl.style.cursor = 'text';
            editorEl.style.backgroundColor = 'white';

            // 이벤트 리스너 제거
            editorEl.removeEventListener('keydown', preventEdit, true);
            editorEl.removeEventListener('paste', preventEdit, true);
            editorEl.removeEventListener('drop', preventEdit, true);
            editorEl.removeEventListener('cut', preventEdit, true);
        }

        // 2. 툴바 활성화
        const toolbar = document.querySelector('.toastui-editor-toolbar');
        if (toolbar) {
            toolbar.style.pointerEvents = 'auto';
            toolbar.style.opacity = '1';

            // 툴바 버튼 활성화
            toolbar.querySelectorAll('button').forEach(btn => {
                btn.disabled = false;
                btn.style.cursor = 'pointer';
            });
        }

        console.log('✅ Toast Editor 편집 모드 완료');
    }, 200);
}

// ✅ 편집 방지 함수
function preventEdit(e) {
    e.preventDefault();
    e.stopPropagation();
    console.log('편집 차단됨');
    return false;
}
function enterEditMode() {
    if (!selectedItem || selectedItemType !== 'note') return;

    const titleEl = document.getElementById('itemTitle');
    const editorArea = document.getElementById('editorArea');

    originalTitle = titleEl.textContent;

    // ✅ Viewer에서는 originalContent 이미 있음
    if (toastEditor && isViewerMode) {
        // Viewer 제거하고 Editor로 재생성
        toastEditor.destroy();

        toastEditor = new toastui.Editor({
            el: editorArea,
            height: '70vh',
            initialEditType: 'wysiwyg',
            initialValue: originalContent,
            previewStyle: 'vertical',
            hideModeSwitch: false,
            toolbarItems: [
                ['heading', 'bold', 'italic', 'strike'],
                ['hr', 'quote'],
                ['ul', 'ol', 'task', 'indent', 'outdent'],
                ['table', 'link'],
                ['code', 'codeblock']
            ]
        });

        isViewerMode = false;
        console.log('✅ Editor로 전환 (편집 가능)');
    }

    titleEl.contentEditable = 'true';
    titleEl.focus();

    const container = document.getElementById('buttonContainer');
    if (container) {
        container.innerHTML = `
            <button class="btn-save" id="saveBtn">💾 저장</button>
            <button class="btn-cancel" id="cancelBtn">❌ 취소</button>
        `;

        setTimeout(() => {
            document.getElementById('saveBtn')?.addEventListener('click', saveNote);
            document.getElementById('cancelBtn')?.addEventListener('click', cancelEdit);
        }, 0);
    }
}


function cancelEdit() {
    if (!selectedItem) return;

    const titleEl = document.getElementById('itemTitle');
    titleEl.textContent = originalTitle;
    titleEl.contentEditable = 'false';

    // ✅ Editor를 Viewer로 재생성 (원본 내용으로)
    if (toastEditor && !isViewerMode) {
        const editorArea = document.getElementById('editorArea');
        toastEditor.destroy();

        toastEditor = toastui.Editor.factory({
            el: editorArea,
            height: '70vh',
            viewer: true,
            initialValue: originalContent
        });

        isViewerMode = true;
        console.log('✅ 취소 - Viewer로 복원');
    }

    updateButtons('note');
}


// ========== 22. 저장 함수 ==========
async function saveNote() {
    if (!selectedItem || selectedItemType !== 'note') return;

    const title = document.getElementById('itemTitle')?.textContent.trim();

    let content;
    if (toastEditor && !isViewerMode) {
        content = toastEditor.getMarkdown();  // Editor 모드에서만 가능
    } else {
        content = originalContent;  // Viewer 모드면 저장된 값 사용
    }

    if (!title) {
        showMessage('제목을 입력해주세요.');
        return;
    }

    try {
        const res = await secureFetch(`/notion/api/notion/${selectedItem.noteIdx}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ title, content })
        });

        const json = await res.json();

        if (json.success) {
            showMessage('저장되었습니다.');

            // ✅ Editor를 Viewer로 재생성
            if (toastEditor) {
                const editorArea = document.getElementById('editorArea');
                originalContent = content;  // 저장된 내용 업데이트
                toastEditor.destroy();

                toastEditor = toastui.Editor.factory({
                    el: editorArea,
                    height: '70vh',
                    viewer: true,
                    initialValue: originalContent
                });

                isViewerMode = true;
            }

            document.getElementById('itemTitle').contentEditable = 'false';
            updateButtons('note');
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

    let content;
    if (toastEditor && !isViewerMode) {
        content = toastEditor.getMarkdown();
    } else {
        content = originalContent;
    }

    console.log('파일 저장:', {fileId: selectedItem.gridfsId, contentLength: content.length});

    try {
        const res = await secureFetch(`/api/files/update/${selectedItem.gridfsId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                content: content
            })
        });

        const json = await res.json();

        if (json.success) {
            showMessage('저장되었습니다.');

            // ✅ newGridfsId로 selectedItem 업데이트
            if (json.newGridfsId) {
                selectedItem.gridfsId = json.newGridfsId;
            }

            // ✅ Editor를 Viewer로 재생성
            if (toastEditor) {
                const editorArea = document.getElementById('editorArea');
                originalContent = content;
                toastEditor.destroy();

                toastEditor = toastui.Editor.factory({
                    el: editorArea,
                    height: '70vh',
                    viewer: true,
                    initialValue: originalContent
                });

                isViewerMode = true;
            }

            updateButtons('file');
            fetchTreeData();  // 리스트 갱신
        } else {
            showMessage(json.message || '저장 실패');
        }
    } catch (e) {
        console.error('저장 오류:', e);
        showMessage('저장 중 오류 발생: ' + e.message);
    }
}


function cancelFileEdit() {
    if (!selectedItem) return;

    // ✅ Editor를 Viewer로 재생성 (원본 내용으로)
    if (toastEditor && !isViewerMode) {
        const editorArea = document.getElementById('editorArea');
        toastEditor.destroy();

        toastEditor = toastui.Editor.factory({
            el: editorArea,
            height: '70vh',
            viewer: true,
            initialValue: originalContent
        });

        isViewerMode = true;
        console.log('✅ 파일 편집 취소 - Viewer로 복원');
    }

    updateButtons('file');
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
    if (!selectedItem || selectedItemType !== 'note') {
        showMessage('노트를 선택해주세요.');
        return;
    }

    console.log('Download note:', selectedItem.noteIdx);

    // ✅ 수정된 경로 확인
    const url = `/notion/api/notion/download/${selectedItem.noteIdx}`;
    console.log('다운로드 URL:', url);

    window.open(url, '_blank');
}

function downloadFile() {
    if (!selectedItem || selectedItemType !== 'file') return;
    window.open(`/api/files/download/${selectedItem.gridfsId}`, '_blank');
}

function downloadSingleFile(gridfsId) {
    window.open(`/api/files/download/${gridfsId}`, '_blank');
}

async function downloadFolder(folderId) {
    console.log('Download folder:', folderId);

    const fileIds = [];
    const noteIds = [];
    let folderStructure = [];

    // ✅ 빈 폴더도 포함하도록 수정
    function collectItems(folder, path = '') {
        const currentPath = path ? path + '/' + folder.folderName : folder.folderName;

        // ✅ 폴더 자체를 먼저 추가 (빈 폴더 표시용)
        folderStructure.push({
            type: 'folder',
            path: currentPath,
            name: ''  // 폴더 자체
        });

        // 파일 수집
        if (folder.files && folder.files.length > 0) {
            folder.files.forEach(f => {
                const fileId = f.id || f._id || f.gridfsId;
                fileIds.push(fileId);
                folderStructure.push({
                    type: 'file',
                    id: fileId,
                    path: currentPath,
                    name: f.originalName
                });
            });
        }

        // 노트 수집
        if (folder.notes && folder.notes.length > 0) {
            folder.notes.forEach(n => {
                noteIds.push(n.noteIdx);
                folderStructure.push({
                    type: 'note',
                    id: n.noteIdx,
                    path: currentPath,
                    name: n.title + '.md',
                    title: n.title,
                    content: n.content
                });
            });
        }

        // 하위 폴더 재귀
        if (folder.subfolders && folder.subfolders.length > 0) {
            folder.subfolders.forEach(sub => collectItems(sub, currentPath));
        }
    }

    // 폴더 찾기
    const findFolder = (folders, targetId) => {
        for (let folder of folders) {
            if (folder.id === targetId || folder._id === targetId || folder.folderId === targetId) {
                return folder;
            }
            if (folder.subfolders) {
                const found = findFolder(folder.subfolders, targetId);
                if (found) return found;
            }
        }
        return null;
    };

    let targetFolder = findFolder(itemsData.fileFolders, folderId);
    if (!targetFolder) {
        targetFolder = findFolder(itemsData.noteFolders, folderId);
    }

    if (!targetFolder) {
        showMessage('폴더를 찾을 수 없습니다.');
        return;
    }

    collectItems(targetFolder);

    console.log('수집된 파일:', fileIds.length + '개');
    console.log('수집된 노트:', noteIds.length + '개');
    console.log('폴더 구조:', folderStructure);

    if (fileIds.length === 0 && noteIds.length === 0) {
        showMessage('폴더에 파일이나 노트가 없습니다.');
        return;
    }

    try {
        // ✅ 백엔드에 파일ID, 노트ID, 폴더구조 모두 전송
        const res = await secureFetch('/api/files/download-folder-zip', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                folderIds: [],
                fileIds: fileIds,
                noteIds: noteIds,
                folderStructure: folderStructure
            })
        });

        if (!res.ok) {
            showMessage('ZIP 다운로드 실패');
            return;
        }

        const blob = await res.blob();
        if (blob.size === 0) {
            showMessage('다운로드할 내용이 없습니다.');
            return;
        }

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = (targetFolder.folderName || 'folder') + '.zip';
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);

        showMessage('다운로드 완료');
    } catch (e) {
        console.error('ZIP 다운로드 오류:', e);
        showMessage('다운로드 중 오류 발생');
    }
}

async function downloadFolderAsZip() {
    console.log('downloadFolderAsZip 호출 - selectedItemType:', selectedItemType);

    if (!selectedItem) {
        showMessage('폴더를 선택해주세요.');
        return;
    }

    // ✅ noteFolder도 허용
    if (selectedItemType !== 'folder' && selectedItemType !== 'noteFolder') {
        showMessage('폴더만 ZIP 다운로드할 수 있습니다.');
        return;
    }

    // ✅ folderId 추출 (folder는 .id, noteFolder는 .folderId)
    const folderId = selectedItem.id || selectedItem.folderId;

    if (!folderId) {
        showMessage('폴더 ID를 찾을 수 없습니다.');
        return;
    }

    console.log('Folder ID:', folderId);
    await downloadFolder(folderId);
}

async function downloadSelectedAsZip() {
    if (selectedItems.length === 0) {
        showMessage('다운로드할 항목을 선택해주세요.');
        return;
    }

    const folderIds = [];
    const fileIds = [];
    const noteIds = [];
    const folderStructure = [];

    console.log('=== Selected Items ===');
    selectedItems.forEach(({ type, item }) => {
        console.log('Type:', type, 'Item:', item);

        if (!type) {
            console.warn('⚠️ type이 없는 아이템:', item);
            return;  // type 없으면 스킵
        }

        if (type === 'folder') {
            folderIds.push(item.id);
        } else if (type === 'file') {
            const fileId = item.id || item.gridfsId;
            fileIds.push(fileId);
            folderStructure.push({
                type: 'file',
                id: fileId,
                path: '',
                name: item.originalName || '파일'
            });
        } else if (type === 'note') {
            noteIds.push(item.noteIdx);
            folderStructure.push({
                type: 'note',
                id: item.noteIdx,
                path: '',
                name: (item.title || '제목없음') + '.md'
            });
        }
    });

    console.log('Folder IDs:', folderIds);
    console.log('File IDs:', fileIds);
    console.log('Note IDs:', noteIds);

    if (fileIds.length === 0 && noteIds.length === 0) {
        showMessage('다운로드할 파일이나 노트가 없습니다.');
        return;
    }

    try {
        // ✅ 노트가 있으면 download-folder-zip, 파일만 있으면 download-zip
        const endpoint = noteIds.length > 0 ?
            '/api/files/download-folder-zip' :
            '/api/files/download-zip';

        console.log('사용할 엔드포인트:', endpoint);

        const res = await secureFetch(endpoint, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                folderIds,
                fileIds,
                noteIds,
                folderStructure
            })
        });

        console.log('Response status:', res.status);

        if (!res.ok) {
            showMessage('ZIP 다운로드 실패');
            return;
        }

        const blob = await res.blob();
        console.log('Blob size:', blob.size);

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'selected.zip';
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);

        showMessage('다운로드 완료');
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
        // ✅ 수정: /notion/api/notion/{noteIdx}
        const res = await secureFetch(`/notion/api/notion/${selectedItem.noteIdx}`, {
            method: 'DELETE'
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
    draggedItem = item;
    draggedType = type;

    e.dataTransfer.effectAllowed = 'move';

    // ✅ textplain으로 JSON 저장
    e.dataTransfer.setData('text/plain', JSON.stringify({item, type}));

    e.target.style.opacity = '0.5';
    console.log('Drag start:', type, item);
}

// ========== 드래그 종료 ==========
function handleDragEnd(e) {
    dragging = false;
    draggedItem = null;
    draggedType = null;
    e.target.style.opacity = '1';
    console.log('Drag end');
}

// ========== 드래그 오버 ==========
function handleDragOver(e) {

    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'; // ✅ 브라우저에 이동 허용 명시
    e.currentTarget.classList.add('drop-target');

}

// ========== 드래그 나가기 ==========
function handleDragLeave(e) {
    e.currentTarget.classList.remove('drop-target');
}

// ========== 노트 드롭 ==========
async function handleNoteDrop(e, targetFolderId) {
    e.preventDefault();
    e.currentTarget.classList.remove('drop-target');

    try {
        const dataStr = e.dataTransfer.getData('text/plain');
        if (!dataStr) {
            console.warn('드롭 데이터가 없습니다.');
            return;
        }

        const {item, type} = JSON.parse(dataStr);

        if (type !== 'note') {
            console.log('노트가 아닙니다:', type);
            return;
        }

        const formData = new FormData();
        formData.append('noteId', item.noteIdx);
        formData.append('targetFolderId', targetFolderId);

        const res = await secureFetch('/api/unified/notes/move', {
            method: 'PUT',
            headers: new Headers({
                [csrfHeader]: csrfToken
            }),
            body: formData
        });

        const result = await res.json();

        if (result.success) {
            showMessage('노트를 이동했습니다.');
            fetchTreeData();
        } else {
            showMessage(result.message || '이동 실패');
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류 발생');
    }
}

// ========== 파일 드롭 ==========
async function handleFileDrop(e, targetFolderId) {
    e.preventDefault();
    e.currentTarget.classList.remove('drop-target');

    try {
        const dataStr = e.dataTransfer.getData('text/plain');
        if (!dataStr) {
            console.warn('드롭 데이터가 없습니다.');
            return;
        }

        const {item, type} = JSON.parse(dataStr);

        if (type !== 'file') {
            console.log('파일이 아닙니다:', type);
            return;
        }

        const formData = new FormData();
        formData.append('fileId', item.id || item.gridfsId);
        if (targetFolderId) {
            formData.append('targetFolderId', targetFolderId);
        }

        const res = await secureFetch('/api/folders/move-file', {
            method: 'PUT',
            headers: new Headers({
                [csrfHeader]: csrfToken
            }),
            body: formData
        });

        const result = await res.json();

        if (result.success) {
            showMessage('파일을 이동했습니다.');
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
function setupRootDropZone() {
    const rootDropZone = document.getElementById('rootDropZone');
    if (!rootDropZone) return;

    rootDropZone.addEventListener('dragover', handleDragOver);
    rootDropZone.addEventListener('dragleave', handleDragLeave);
    rootDropZone.addEventListener('drop', handleRootDrop);
}

// ===============================
// 전역/위임 DnD 핸들러 (drop 누락 방지)
// ===============================
function setupGlobalDnDDelegation() {
    // 1) 브라우저 기본 드롭 불허를 전역에서 허용
    document.addEventListener('dragenter', (e) => { e.preventDefault(); }, true);
    document.addEventListener('dragover',  (e) => { e.preventDefault(); if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'; }, true);

    // 2) 트리 컨테이너 위임: 새로 렌더된 노드에도 drop 가능
    const list = document.getElementById('itemList');
    if (!list) return;

    list.addEventListener('dragover', (e) => {
        e.preventDefault();
        const folderItem = e.target.closest('.folder-item');
        const rootDrop = e.target.closest('#rootDropZone');
        document.querySelectorAll('.drop-target').forEach(x => x.classList.remove('drop-target'));
        if (folderItem) {
            folderItem.classList.add('drop-target');
            if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
        } else if (rootDrop) {
            rootDrop.classList.add('drop-target');
            if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
        }
    });

    list.addEventListener('drop', async (e) => {
        e.preventDefault();
        document.querySelectorAll('.drop-target').forEach(x => x.classList.remove('drop-target'));

        const dataStr = e.dataTransfer?.getData('text/plain');
        if (!dataStr) return;
        let parsed;
        try { parsed = JSON.parse(dataStr); } catch { return; }
        const { item, type } = parsed;

        const folderItem = e.target.closest('.folder-item');
        const rootDrop = e.target.closest('#rootDropZone');

        try {
            if (rootDrop) {
                await handleRootDrop(e); // ✅ 루트 드롭 재사용
                return;
            }
            if (folderItem) {
                const targetFolderId = folderItem.dataset.folderId;
                if (!targetFolderId) return;
                // 타입에 맞는 기존 API 호출 재사용
                if (type === 'file' || type === 'folder') {
                    await manualMoveToFolder(item, type, targetFolderId);
                } else if (type === 'note' || type === 'noteFolder') {
                    await manualMoveToFolder(item, type, targetFolderId);
                }
                showMessage('폴더로 이동했습니다.');
                fetchTreeData();
            }
        } catch (err) {
            console.error('위임 drop 이동 오류:', err);
            showMessage('이동 중 오류가 발생했습니다.');
        }
    });
}

// ===============================
// 루트 드롭 처리 (folder/note/file/noteFolder)
// ===============================
async function handleRootDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.classList.remove('drop-target');

    try {
        const dataStr = e.dataTransfer.getData('text/plain');
        if (!dataStr) return;
        const { item, type } = JSON.parse(dataStr);

        if (type === 'note') {
            const fd = new FormData();
            fd.append('noteId', item.noteIdx);
            // 루트 이동: targetFolderId 키 자체를 보내지 않음 (서버에서 null로 간주)
            const res = await secureFetch('/api/unified/notes/move', {
                method: 'PUT',
                headers: new Headers({ [csrfHeader]: csrfToken }),
                body: fd
            });
            const result = await res.json();
            if (result.success) showMessage('노트를 루트로 이동했습니다.'), fetchTreeData();
        }
        else if (type === 'noteFolder') {
            const res = await secureFetch(`/api/unified/note-folders/${item.folderId}/move`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetFolderId: null }) // null = 루트
            });
            const result = await res.json();
            if (result.success) showMessage('폴더를 루트로 이동했습니다.'), fetchTreeData();
        }
        else if (type === 'file') {
            const fd = new FormData();
            fd.append('fileId', item.id || item.gridfsId);
            // 루트 이동: targetFolderId 미전송
            const res = await secureFetch('/api/folders/move-file', {
                method: 'PUT',
                headers: new Headers({ [csrfHeader]: csrfToken }),
                body: fd
            });
            const result = await res.json();
            if (result.success) showMessage('파일을 루트로 이동했습니다.'), fetchTreeData();
        }
        else if (type === 'folder') {
            const res = await secureFetch(`/api/folders/${item.id}/move`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetFolderId: null })
            });
            const result = await res.json();
            if (result.success) showMessage('폴더를 루트로 이동했습니다.'), fetchTreeData();
        }
    } catch (err) {
        console.error('루트 드롭 오류:', err);
        showMessage('이동 중 오류가 발생했습니다.');
    }
}