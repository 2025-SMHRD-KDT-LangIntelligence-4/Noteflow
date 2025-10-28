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
let categoryHierarchy = {};  // { "국어": { "문법": ["품사", "문장성분"] } }
let currentCategory = { large: null, medium: null, small: null };

// 페이지이동시 취소시키기 컨트롤러
let abortController = null;

// 편집 모드 백업
let originalContent = '';
let originalTitle = '';
let isViewerMode = false;
let currentTags = [];
// ========== 3. 초기화 ==========
document.addEventListener('DOMContentLoaded', () => {
    setupTabs();
    setupSearch();
    setupFileInput();
    setupCreateFolder();
    fetchTreeData();
    setupTagInput();
    setupRootDropZone();
    setupGlobalDnDDelegation();
    setupCategorySelects();
    setupAddCategoryButton();
    loadCategories();

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
                        children.style.display = 'flex';
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
                        children.style.display = 'flex';
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

    //  검색 결과 메시지

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
                children.style.display = 'flex';
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
    // 이전 요청이 진행 중이면 취소 (거의 리소스 안 먹음)
    if (abortController) {
        abortController.abort();
    }

    // 새 AbortController 생성 (매우 가벼움)
    abortController = new AbortController();

    try {
        const [notesRes, filesRes] = await Promise.all([
            secureFetch('/api/unified/notes/tree', {
                signal: abortController.signal  // ⭐ signal 추가
            }),
            secureFetch('/api/unified/files/tree', {
                signal: abortController.signal  // ⭐ signal 추가
            })
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
        // AbortError는 정상적인 취소이므로 무시
        if (e.name === 'AbortError') {

            return;  // 에러 아님!
        }
        // console.error('트리 데이터 불러오기 실패:', e);
    }
}
window.addEventListener('beforeunload', () => {
    if (abortController) {
        abortController.abort();
    }
});
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

        const { item, type } = JSON.parse(dataStr);

        if (type === 'note') {
            // ✅ 수정: URL에 noteId 포함
            const res = await secureFetch(`/api/unified/notes/${item.noteIdx}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId })
            });

            const result = await res.json();
            if (result.success) {
                showMessage('노트가 이동되었습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message);
            }
        } else if (type === 'noteFolder') {
            if (item.folderId === targetFolderId) {
                showMessage('같은 폴더입니다.');
                return;
            }

            const res = await secureFetch(`/api/unified/note-folders/${item.folderId}/move`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetFolderId })
            });

            const result = await res.json();
            if (result.success) {
                showMessage('폴더가 이동되었습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message);
            }
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류가 발생했습니다.');
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

        const { item, type } = JSON.parse(dataStr);

        if (type === 'file') {
            // ✅ 수정: URL에 fileId 포함
            const fileId = item.id || item.gridfsId;
            const res = await secureFetch(`/api/unified/files/${fileId}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId })
            });

            const result = await res.json();
            if (result.success) {
                showMessage('파일이 이동되었습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message);
            }
        } else if (type === 'folder') {
            // ✅ 수정: URL 변경
            if (item.id === targetFolderId) {
                showMessage('같은 폴더입니다.');
                return;
            }

            const res = await secureFetch(`/api/unified/folders/${item.id}/move`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetFolderId })
            });

            const result = await res.json();
            if (result.success) {
                showMessage('폴더가 이동되었습니다.');
                fetchTreeData();
            } else {
                showMessage(result.message);
            }
        }
    } catch (err) {
        console.error('드롭 오류:', err);
        showMessage('이동 중 오류가 발생했습니다.');
    }
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
//------------------------제목슬라이더----------------------//
function createNoteElement(note, depth) {
    const div = document.createElement('div');
    div.className = 'note-item';
    div.draggable = true;
    div.style.paddingLeft = (depth * 20 + 30) + 'px';
    div.dataset.noteIdx = note.noteIdx;

    const container = document.createElement('div');
    container.className = 'note-item-container';

    // ✅ 체크박스
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'item-checkbox';
    checkbox.style.marginRight = '8px';
    checkbox.style.width = '16px';
    checkbox.style.height = '16px';
    checkbox.addEventListener('click', (e) => e.stopPropagation());

    // ✅ 아이콘
    const icon = document.createElement('span');
    icon.className = 'item-icon';
    icon.innerHTML = '📝';

    // ✅ 제목
    const titleWrapper = document.createElement('div');
    titleWrapper.className = 'note-title-wrapper';
    const title = document.createElement('span');
    title.className = 'note-title';
    title.textContent = note.title;
    titleWrapper.appendChild(title);

    // ✅ 조립
    container.appendChild(checkbox);
    container.appendChild(icon);
    container.appendChild(titleWrapper);
    div.appendChild(container);

    // ✅ 마우스 올릴 때 제목 길이 확인 후 슬라이드
    div.addEventListener('mouseenter', () => {
        const titleEl = div.querySelector('.note-title');
        const wrapperEl = div.querySelector('.note-title-wrapper');

        const titleWidth = titleEl.scrollWidth;
        const wrapperWidth = wrapperEl.clientWidth;

        // 제목이 wrapper보다 길 때만 슬라이드 시작
        if (titleWidth > wrapperWidth) {
            const distance = titleWidth - wrapperWidth;
            const duration = distance * 15; // px당 속도 (조정 가능)
            titleEl.style.setProperty('--scroll-distance', `-${distance}px`);
            titleEl.style.setProperty('--scroll-duration', `${duration}ms`);
            titleEl.classList.add('scrolling');
        }
    });

    // ✅ 마우스가 떠나면 슬라이드 초기화
    div.addEventListener('mouseleave', () => {
        const titleEl = div.querySelector('.note-title');
        titleEl.classList.remove('scrolling');
        titleEl.style.transform = 'translateX(0)';
    });

    // ✅ 선택 관련 기존 로직
    div.addEventListener('click', (e) => {
        if (dragging) return;
        e.stopPropagation();

        const multi = e.ctrlKey || e.metaKey;
        if (multi && currentTab === 'notes') {
            toggleMultiFileSelection({ item: note, el: div, type: 'note' });
            checkbox.checked = selectedItems.some(si => si.item.noteIdx === note.noteIdx);
        } else {
            clearMultiSelection();
            selectNote(note, div);
        }
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
    clearMultiSelection();
    hideCategorySelectArea();
    selectedItem = note;
    selectedItemType = 'note';
    updateSelectedState(el);
    showNoteContent(note);
}



// ========== updateMultiSelectionUI 함수 추가 ==========
function updateMultiSelectionUI() {
    const container = document.getElementById('buttonContainer');
    const bulkDeleteBtn = document.getElementById('bulkDeleteBtn');

    if (!container) return;

    // ✅ 삭제 버튼 표시/숨김
    if (bulkDeleteBtn) {
        bulkDeleteBtn.style.display = selectedItems.length > 0 ? 'inline-block' : 'none';
    }

    if (selectedItems.length > 0) {
        selectedItems.forEach(si => {
            si.el.classList.add('multi-selected');
            const cb = si.el.querySelector('.item-checkbox');
            if (cb) cb.checked = true;
        });

        container.innerHTML = `
            <button class="btn-download" onclick="downloadSelectedAsZip()">📦 ZIP 다운로드 (${selectedItems.length})</button>
            <button class="btn-cancel" onclick="clearMultiSelection()">✖ 선택 해제</button>
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
    hideCategorySelectArea();
}

function selectFolder(folder, el, type) {
    clearMultiSelection();
    hideCategorySelectArea();
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
    document.getElementById('welcomeMessage').style.display = 'flex';
}
// ========== 16. 다중 선택 ==========
function toggleMultiFileSelection(item) {
    // 타입 추론
    if (!item.type) {
        if (item.item.gridfsId) {
            item.type = 'file';
        } else if (item.item.noteIdx) {
            item.type = 'note';
        } else if (item.item.folderId || item.item.id) {
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

        // ✅ 전체선택 상태 해제
        isAllSelected = false;
        const selectAllBtn = document.getElementById('selectAllBtn');
        if (selectAllBtn) selectAllBtn.textContent = '🔲 전체선택';
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
    hideCategorySelectArea();

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
        editorArea.style.display = 'flex';
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
                    height: '510px',
                    viewer: true,
                    initialValue: originalContent
                });

                isViewerMode = true;  // ✅ 플래그 설정

                updateButtons('note');

            } catch (e) {
                console.error('Toast Editor 생성 실패:', e);
                if (contentEl) {
                    editorArea.style.display = 'none';
                    contentEl.style.display = 'flex';
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
    hideCategorySelectArea();

    const titleEl = document.getElementById('itemTitle');
    const contentEl = document.getElementById('itemContent');
    const previewArea = document.getElementById('previewArea');
    const pdfPreview = document.getElementById('pdfPreview');
    const imagePreview = document.getElementById('imagePreview');
    const hwpPreview = document.getElementById('hwpPreview'); // 한글뷰어 추가
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
            previewArea.style.display = 'flex';
            pdfPreview.style.display = 'flex';
            imagePreview.style.display = 'none';
            hwpPreview.style.display = 'none';
            pdfPreview.src = `/api/files/preview/${file.gridfsId}`;
        }
        // HWP/HWPX 미리보기  추가
        else if (['hwp', 'hwpx'].includes(ext)) {
            previewArea.style.display = 'flex';
            pdfPreview.style.display = 'none';
            imagePreview.style.display = 'none';
            hwpPreview.style.display = 'block';

            const hwpContainer = document.getElementById('hwpContainer');

            // ⭐ HWP 로딩 대기 ⭐
            if (typeof window.HWP === 'undefined') {
                hwpContainer.innerHTML = '<div style="text-align:center;padding:40px;">HWP 라이브러리 로딩 중...</div>';

                // 최대 3초 대기
                let attempts = 0;
                const checkInterval = setInterval(() => {
                    attempts++;
                    if (typeof window.HWP !== 'undefined') {
                        clearInterval(checkInterval);
                        renderHWPFile(hwpContainer, file);
                    } else if (attempts > 30) {
                        clearInterval(checkInterval);
                        hwpContainer.innerHTML = '<div style="padding:40px;color:red;">HWP 라이브러리를 불러올 수 없습니다.</div>';
                    }
                }, 100);
            } else {
                await renderHWPFile(hwpContainer, file);
            }
        }

        // 이미지
        else if (imageExts.includes(ext)) {
            previewArea.style.display = 'flex';
            pdfPreview.style.display = 'none';
            imagePreview.style.display = 'flex';
            imagePreview.src = `/api/files/download/${file.gridfsId}`;
        }

        // Excel/CSV
        else if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') {
            spreadsheetArea.style.display = 'flex';

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
                    contentEl.style.display = 'flex';
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

            editorArea.style.display = 'flex';

            if (toastEditor) {
                toastEditor.destroy();
                toastEditor = null;
            }

            toastEditor = toastui.Editor.factory({
                el: editorArea,
                height: '510px',
                viewer: true,
                initialValue: text
            });

            isViewerMode = true;

        }

        // 기타
        else {
            contentEl.style.display = 'flex';
            contentEl.value = `파일명: ${file.originalName}\n확장자: ${ext}\n\n이 파일 형식은 미리보기를 지원하지 않습니다.`;
            contentEl.readOnly = true;
        }

    } catch (e) {
        console.error('파일 로드 오류:', e);
        contentEl.style.display = 'flex';
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
            height: '510px',
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
    contentEl.style.display = 'flex';
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
    const hwpPreview = document.getElementById('hwpPreview');

    if (contentEl) contentEl.style.display = 'none';
    if (editorArea) editorArea.style.display = 'none';
    if (previewArea) previewArea.style.display = 'none';
    if (spreadsheetArea) spreadsheetArea.style.display = 'none';
    if (hwpPreview) hwpPreview.style.display = 'none';
}

// ========== 19. HandsOnTable 초기화 ==========

// ========== 20. 버튼 업데이트 ==========
function updateButtons(type) {
    const container = document.getElementById('buttonContainer');
    if (!container) return;

    container.innerHTML = '';

    if (type === 'note') {
        container.innerHTML = `
            <button class="btn-edit" id="editBtn">✏️ 수정</button>
            <button class="btn-exam" id="goToExamBtn">📝 문제은행 가기</button>
            <button class="btn-download" id="downloadBtn">💾 다운로드</button>
            <button class="btn-delete" id="deleteBtn">🗑️ 삭제</button>
        `;

        setTimeout(() => {
            const editBtn = document.getElementById('editBtn');
            const examBtn = document.getElementById('goToExamBtn');
            const downloadBtn = document.getElementById('downloadBtn');
            const deleteBtn = document.getElementById('deleteBtn');

            if (editBtn) editBtn.addEventListener('click', enterEditMode);
            if (examBtn) examBtn.addEventListener('click', goToExamCreate);
            if (downloadBtn) downloadBtn.addEventListener('click', downloadNote);
            if (deleteBtn) deleteBtn.addEventListener('click', deleteNotePrompt);
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
async function goToExamCreate() {
    if (!selectedItem || selectedItemType !== 'note') {
        showMessage('노트를 선택해주세요.');
        return;
    }

    const noteIdx = selectedItem.noteIdx;
    const noteTitle = selectedItem.title;
    const keywords = currentTags || [];



    try {
        const headers = {
            'Content-Type': 'application/json'
        };

        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }



        const response = await fetch('/exam/prepare-from-note', {
            method: 'POST',
            headers: headers,
            credentials: 'include',
            body: JSON.stringify({
                noteIdx: noteIdx,
                noteTitle: noteTitle,
                keywords: keywords
            })
        });



        const contentType = response.headers.get('content-type');


        if (!response.ok) {
            console.error('❌ HTTP 오류:', response.status, response.statusText);
            showMessage(`서버 오류 (${response.status}): ${response.statusText}`);
            return;
        }

        // Content-Type 체크
        if (!contentType || !contentType.includes('application/json')) {
            console.error('❌ JSON이 아닌 응답:', contentType);
            const text = await response.text();
            console.error('응답 내용:', text);
            showMessage('서버가 잘못된 응답을 반환했습니다.');
            return;
        }

        const result = await response.json();


        if (result.success) {

            window.location.href = '/exam/create';
        } else {

            showMessage('오류: ' + result.message);
        }

    } catch (error) {

        showMessage('문제은행으로 이동하는 중 오류가 발생했습니다: ' + error.message);
    }
}
// ========== 21. 편집 모드 ==========

function enterEditMode() {
    if (!selectedItem || selectedItemType !== 'note') return;

    const titleEl = document.getElementById('itemTitle');
    const editorArea = document.getElementById('editorArea');
    const tagInputArea = document.getElementById('tagInputArea');

    originalTitle = titleEl.textContent;

    // 기존 태그 로드
    currentTags = selectedItem.keywords || [];
    renderTags();
    showCategorySelectArea();
    // ✅ 태그 입력 영역 표시
    if (tagInputArea) {
        tagInputArea.style.display = 'flex';
    }

    // Viewer → Editor 전환
    if (toastEditor && isViewerMode) {
        const content = originalContent;
        toastEditor.destroy();
        toastEditor = new toastui.Editor({
            el: editorArea,
            height: '510px',
            initialEditType: 'wysiwyg',
            initialValue: content,
            previewStyle: 'vertical',
            hideModeSwitch: false,
            toolbarItems: [
                ['heading', 'bold', 'italic', 'strike'],
                ['hr', 'quote'],
                ['ul', 'ol', 'task', 'indent', 'outdent'],
                ['table', 'link', 'code', 'codeblock']
            ]
        });
        isViewerMode = false;

    }

    titleEl.contentEditable = true;
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
// ✅ 태그 렌더링
function renderTags() {
    const tagList = document.getElementById('tagDisplay');
    if (!tagList) return;

    tagList.innerHTML = ''; // ✅ 초기화

    currentTags.forEach(tag => {
        const chip = document.createElement('span');
        chip.className = 'tag-chip';
        chip.innerHTML = `${tag} <span class="remove-tag">×</span>`;

        const removeBtn = chip.querySelector('.remove-tag');
        removeBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            removeTag(tag);
        });

        tagList.appendChild(chip); // ✅ appendChild로 추가
    });
}

//  태그 추가
function addTag(tagName) {
    const name = tagName.trim();
    if (!name) return;

    if (currentTags.includes(name)) {
        showMessage('이미 추가된 태그입니다.');
        return;
    }

    if (currentTags.length >= 5) {
        showMessage('태그는 최대 5개까지 추가할 수 있습니다.');
        return;
    }

    currentTags.push(name);
    renderTags();

    const input = document.getElementById('tagInput');
    if (input) input.value = '';
}

//  태그 제거
function removeTag(tagName) {
    currentTags = currentTags.filter(t => t !== tagName);
    renderTags();
}

//  태그 입력 이벤트 설정
function setupTagInput() {
    const input = document.getElementById('tagInput');
    if (!input) return;

    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addTag(input.value);
        }
    });
}
function cancelEdit() {
    if (!selectedItem) return;

    const titleEl = document.getElementById('itemTitle');
    const tagInputArea = document.getElementById('tagInputArea');

    titleEl.textContent = originalTitle;
    titleEl.contentEditable = false;
    hideCategorySelectArea();
    // ✅ 태그 입력 영역 숨김
    if (tagInputArea) {
        tagInputArea.style.display = 'none';
    }

    // Editor → Viewer 전환
    if (toastEditor && !isViewerMode) {
        const editorArea = document.getElementById('editorArea');
        toastEditor.destroy();
        toastEditor = toastui.Editor.factory({
            el: editorArea,
            height: '510px',
            viewer: true,
            initialValue: originalContent
        });
        isViewerMode = true;

    }

    updateButtons('note');
}


// ========== 22. 저장 함수 ==========
async function saveNote() {
    if (!selectedItem || selectedItemType !== 'note') return;

    const title = document.getElementById('itemTitle')?.textContent.trim();
    let content = '';

    if (toastEditor && !isViewerMode) {
        content = toastEditor.getMarkdown();
    } else {
        content = originalContent;
    }

    if (!title) {
        showMessage('제목을 입력하세요.');
        return;
    }

    // ✅ 변수를 try 블록 밖에서 선언
    const largeSelect = document.getElementById('largeCategorySelect');
    const mediumSelect = document.getElementById('mediumCategorySelect');
    const smallSelect = document.getElementById('smallCategorySelect');

    try {
        const response = await secureFetch(`/notion/api/notion/${selectedItem.noteIdx}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title: title,
                content: content,
                keywords: currentTags,
                // ✅ 카테고리 전송
                largeCategory: largeSelect?.value || null,
                mediumCategory: mediumSelect?.value || null,
                smallCategory: smallSelect?.value || null
            })
        });

        const json = await response.json();

        if (json.success) {
            showMessage('저장되었습니다.');

            // ✅ 영역 숨김
            hideCategorySelectArea();
            const tagInputArea = document.getElementById('tagInputArea');
            if (tagInputArea) tagInputArea.style.display = 'none';

            // Editor → Viewer 전환
            if (toastEditor) {
                const editorArea = document.getElementById('editorArea');
                originalContent = content;
                toastEditor.destroy();
                toastEditor = toastui.Editor.factory({
                    el: editorArea,
                    height: '510px',
                    viewer: true,
                    initialValue: originalContent
                });
                isViewerMode = true;
            }

            document.getElementById('itemTitle').contentEditable = false;
            updateButtons('note');
            fetchTreeData();  // ✅ 폴더 구조 새로고침
        } else {
            showMessage(json.message);
        }
    } catch (e) {
        console.error('저장 오류:', e);
        showMessage('저장 중 오류가 발생했습니다.');
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
                    height: '510px',
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
            height: '510px',
            viewer: true,
            initialValue: originalContent
        });

        isViewerMode = true;

    }

    updateButtons('file');
}

// ========== 23. 다운로드 함수 ==========
function downloadNote() {
    if (!selectedItem || selectedItemType !== 'note') {
        showMessage('노트를 선택해주세요.');
        return;
    }



    // ✅ 수정된 경로 확인
    const url = `/notion/api/notion/download/${selectedItem.noteIdx}`;


    window.open(url, '_blank');
}

function downloadSingleFile(gridfsId) {
    window.open(`/api/files/download/${gridfsId}`, '_blank');
}

async function downloadFolder(folderId) {


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


    await downloadFolder(folderId);
}

async function downloadSelectedAsZip() {
    if (selectedItems.length === 0) {
        alert('선택된 항목이 없습니다.');
        return;
    }

    const folderIds = [];
    const fileIds = [];
    const noteIds = [];
    const folderStructure = [];

    // ⭐ 폴더 구조 재귀적 수집 함수 ⭐
    function collectFolderItems(folder, basePath = '') {
        const currentPath = basePath ? `${basePath}/${folder.folderName}` : folder.folderName;

        // 폴더 자체 추가
        folderStructure.push({
            type: 'folder',
            path: currentPath,
            name: ''  // 폴더는 path에 이름 포함됨
        });

        // 폴더 내 파일 수집
        if (folder.files && folder.files.length > 0) {
            folder.files.forEach(f => {
                const fileId = f.id || f.gridfsId;
                fileIds.push(fileId);
                folderStructure.push({
                    type: 'file',
                    id: fileId,
                    path: currentPath,  // ⭐ 폴더 경로 포함
                    name: f.originalName
                });
            });
        }

        // 폴더 내 노트 수집
        if (folder.notes && folder.notes.length > 0) {
            folder.notes.forEach(n => {
                noteIds.push(n.noteIdx);
                folderStructure.push({
                    type: 'note',
                    id: n.noteIdx,
                    path: currentPath,  // ⭐ 폴더 경로 포함
                    name: n.title + '.md',
                    title: n.title,
                    content: n.content
                });
            });
        }

        // 하위 폴더 재귀 처리
        if (folder.subfolders && folder.subfolders.length > 0) {
            folder.subfolders.forEach(sub => {
                collectFolderItems(sub, currentPath);
            });
        }
    }

    // ⭐ 선택된 항목 처리 ⭐
    selectedItems.forEach(({ type, item }) => {
        if (type === 'folder') {
            // ⭐ 폴더: 재귀적으로 모든 자식 항목 수집
            folderIds.push(item.id);
            collectFolderItems(item, '');  // basePath는 빈 문자열 (루트부터 시작)
        }
        else if (type === 'noteFolder') {
            // ⭐ NoteFolder: 재귀적으로 모든 자식 항목 수집
            collectFolderItems(item, '');
        }
        else if (type === 'file') {
            // ⚠️ 개별 파일: 루트에 저장
            const fileId = item.id || item.gridfsId;
            fileIds.push(fileId);
            folderStructure.push({
                type: 'file',
                id: fileId,
                path: '',  // 루트
                name: item.originalName
            });
        }
        else if (type === 'note') {
            // ⚠️ 개별 노트: 루트에 저장
            noteIds.push(item.noteIdx);
            folderStructure.push({
                type: 'note',
                id: item.noteIdx,
                path: '',  // 루트
                name: item.title + '.md'
            });
        }
    });

    if (fileIds.length === 0 && noteIds.length === 0) {
        alert('다운로드할 파일이나 노트가 없습니다.');
        return;
    }

    try {
        const endpoint = noteIds.length > 0
            ? '/api/files/download-folder-zip'
            : '/api/files/download-zip';

        const res = await secureFetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                folderIds,
                fileIds,
                noteIds,
                folderStructure
            })
        });

        if (!res.ok) {
            alert('ZIP 다운로드 실패');
            return;
        }

        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'selected.zip';
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);

        console.log('다운로드 완료');
    } catch (e) {
        console.error('ZIP 다운로드 에러:', e);
        alert('다운로드 중 오류가 발생했습니다.');
    }
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
    const name = prompt('📁 폴더 이름을 입력하세요');
    if (!name || !name.trim()) return;

    try {
        if (currentTab === 'files') {
            // ✅ 파일 폴더 생성 (MongoDB)
            const params = new URLSearchParams();
            params.set('folderName', name.trim());

            if (selectedItemType === 'folder' && selectedItem?.id) {
                params.set('parentFolderId', selectedItem.id);
            }

            const res = await secureFetch(`/api/unified/files/folder?${params.toString()}`, {
                method: 'POST',
                headers: new Headers({
                    [csrfHeader]: csrfToken
                })
            });

            const json = await res.json();
            json.success ? (showMessage('폴더 생성 완료'), fetchTreeData()) : showMessage(json.message);

        } else {
            // ✅ 노트 폴더 생성
            const params = new URLSearchParams();
            params.set('folderName', name.trim());

            if (selectedItemType === 'noteFolder' && selectedItem?.folderId) {
                params.set('parentFolderId', selectedItem.folderId);
            }

            const res = await secureFetch(`/api/unified/notes/folder?${params.toString()}`, {
                method: 'POST',
                headers: new Headers({
                    [csrfHeader]: csrfToken
                })
            });

            const json = await res.json();
            json.success ? (showMessage('폴더 생성 완료'), fetchTreeData()) : showMessage(json.message);
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

}

// ========== 드래그 종료 ==========
function handleDragEnd(e) {
    dragging = false;
    draggedItem = null;
    draggedType = null;
    e.target.style.opacity = '1';
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

// ========== 28. 헬퍼 함수 ==========
async function secureFetch(url, options = {}) {
    // Headers 처리
    const headers = options.headers instanceof Headers
        ? options.headers
        : new Headers(options.headers || {});

    if (csrfToken) {
        headers.set(csrfHeader, csrfToken);
    }

    // ✅ 새 객체 생성 (원본 수정 안함)
    return fetch(url, {
        credentials: 'same-origin',
        cache: 'no-store',
        ...options,  // 기존 options 적용
        headers,     // headers 덮어쓰기

    });
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

}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
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

        // ⭐ Note를 루트로 이동 ⭐
        if (type === 'note') {
            const res = await secureFetch(`/api/unified/notes/${item.noteIdx}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId: null })  // ⭐ null로 명시
            });

            const result = await res.json();

            if (result.success) {
                console.log('노트를 루트로 이동 성공');
                fetchTreeData();
            } else {
                console.error('노트 이동 실패:', result.message);
            }
        }
        // ⭐ NoteFolder를 루트로 이동 ⭐
        else if (type === 'noteFolder') {
            const res = await secureFetch(`/api/unified/note-folders/${item.folderId}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId: null })  // ⭐ null로 명시
            });

            const result = await res.json();

            if (result.success) {
                console.log('폴더를 루트로 이동 성공');
                fetchTreeData();
            } else {
                console.error('폴더 이동 실패:', result.message);
            }
        }
        // ⭐ File을 루트로 이동 ⭐
        else if (type === 'file') {
            const res = await secureFetch(`/api/unified/files/${item.id || item.gridfsId}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId: null })  // ⭐ null로 명시
            });

            const result = await res.json();

            if (result.success) {
                console.log('파일을 루트로 이동 성공');
                fetchTreeData();
            } else {
                console.error('파일 이동 실패:', result.message);
            }
        }
        // ⭐ Folder를 루트로 이동 ⭐
        else if (type === 'folder') {
            const res = await secureFetch(`/api/unified/folders/${item.id}/move`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrfHeader && csrfToken ? { [csrfHeader]: csrfToken } : {})
                },
                body: JSON.stringify({ targetFolderId: null })  // ⭐ null로 명시
            });

            const result = await res.json();

            if (result.success) {
                console.log('폴더를 루트로 이동 성공');
                fetchTreeData();
            } else {
                console.error('폴더 이동 실패:', result.message);
            }
        }

    } catch (err) {
        console.error('루트 드롭 에러:', err);
    }
}

// ========================================
// 1. 카테고리 데이터 로드
// ========================================

/**
 * 서버에서 카테고리 계층 데이터 로드
 */
async function loadCategories() {
    try {
        const response = await secureFetch('/api/categories/hierarchy');
        if (!response.ok) {
            throw new Error('카테고리 로드 실패');
        }

        const data = await response.json();

        categoryHierarchy = data.hierarchy;
        populateLargeCategories(data.largeCategories);


    } catch (e) {
    }
}

/**
 * 대분류 셀렉트박스 채우기
 */
function populateLargeCategories(largeCategories) {
    const select = document.getElementById('largeCategorySelect');
    if (!select) return;

    select.innerHTML = '<option value="">대분류 선택</option>';

    largeCategories.forEach(cat => {
        const option = document.createElement('option');
        option.value = cat;
        option.textContent = cat;
        select.appendChild(option);
    });
}

// ========================================
// 2. 카테고리 선택 이벤트 설정
// ========================================

/**
 * 카테고리 셀렉트박스 이벤트 바인딩
 */
function setupCategorySelects() {
    const largeSelect = document.getElementById('largeCategorySelect');
    const mediumSelect = document.getElementById('mediumCategorySelect');
    const smallSelect = document.getElementById('smallCategorySelect');

    if (!largeSelect || !mediumSelect || !smallSelect) {
        console.warn('카테고리 셀렉트 박스를 찾을 수 없습니다.');
        return;
    }

    // ✅ 대분류 선택
    largeSelect.addEventListener('change', (e) => {
        const large = e.target.value;
        currentCategory.large = large;
        currentCategory.medium = null;
        currentCategory.small = null;

        if (!large) {
            mediumSelect.disabled = true;
            smallSelect.disabled = true;
            mediumSelect.innerHTML = '<option value="">중분류 선택</option>';
            smallSelect.innerHTML = '<option value="">소분류 선택</option>';
            return;
        }

        // 중분류 채우기
        mediumSelect.disabled = false;
        mediumSelect.innerHTML = '<option value="">중분류 선택</option>';
        smallSelect.disabled = true;
        smallSelect.innerHTML = '<option value="">소분류 선택</option>';

        const mediumCategories = Object.keys(categoryHierarchy[large] || {});
        mediumCategories.forEach(cat => {
            const option = document.createElement('option');
            option.value = cat;
            option.textContent = cat;
            mediumSelect.appendChild(option);
        });

    });

    // ✅ 중분류 선택
    mediumSelect.addEventListener('change', (e) => {
        const medium = e.target.value;
        currentCategory.medium = medium;
        currentCategory.small = null;

        if (!medium) {
            smallSelect.disabled = true;
            smallSelect.innerHTML = '<option value="">소분류 선택</option>';
            return;
        }

        // 소분류 채우기
        smallSelect.disabled = false;
        smallSelect.innerHTML = '<option value="">소분류 선택</option>';

        const smallCategories = categoryHierarchy[currentCategory.large][medium] || [];
        smallCategories.forEach(cat => {
            const option = document.createElement('option');
            option.value = cat;
            option.textContent = cat;
            smallSelect.appendChild(option);
        });

    });

    // ✅ 소분류 선택
    smallSelect.addEventListener('change', (e) => {
        currentCategory.small = e.target.value;

    });
}

// ========================================
// 3. 새 카테고리 추가 버튼
// ========================================

/**
 * 새 카테고리 추가 버튼 이벤트
 */
function setupAddCategoryButton() {
    const addBtn = document.getElementById('addCategoryBtn');
    if (!addBtn) {
        console.warn('카테고리 추가 버튼을 찾을 수 없습니다.');
        return;
    }

    addBtn.addEventListener('click', async () => {
        const large = prompt('대분류를 입력하세요:');
        if (!large || large.trim() === '') {
            showMessage('대분류는 필수입니다.');
            return;
        }

        const medium = prompt('중분류를 입력하세요 (선택):');
        const small = prompt('소분류를 입력하세요 (선택):');

        try {
            const response = await secureFetch('/api/categories/add', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    large: large.trim(),
                    medium: medium ? medium.trim() : null,
                    small: small ? small.trim() : null
                })
            });

            const result = await response.json();
            showMessage(result.message);

            if (result.success) {
                await loadCategories();  // 카테고리 목록 새로고침
            }
        } catch (e) {
            console.error('카테고리 추가 오류:', e);
            showMessage('카테고리 추가 중 오류가 발생했습니다.');
        }
    });
}

// ========================================
// 4. 편집 모드 - 카테고리 영역 표시
// ========================================

/**
 * 편집 모드 진입 시 카테고리 셀렉트 표시 및 현재 값 설정
 */function showCategorySelectArea() {
    const categorySelectArea = document.getElementById('categorySelectArea');
    const tagInputArea = document.getElementById('tagInputArea');

    if (!categorySelectArea) return;

    // 카테고리/태그 영역 표시
    categorySelectArea.style.display = 'flex';
    if (tagInputArea) tagInputArea.style.display = 'flex';

    // ✅ 기존 카테고리 데이터 로드
    if (selectedItem && selectedItem.category) {
        const [large, medium, small] = selectedItem.category;

        if (large) {
            const largeSelect = document.getElementById('largeCategorySelect');
            largeSelect.value = large;
            largeSelect.dispatchEvent(new Event('change'));

            setTimeout(() => {
                if (medium) {
                    const mediumSelect = document.getElementById('mediumCategorySelect');
                    mediumSelect.value = medium;
                    mediumSelect.dispatchEvent(new Event('change'));

                    setTimeout(() => {
                        if (small) {
                            const smallSelect = document.getElementById('smallCategorySelect');
                            smallSelect.value = small;
                        }
                    }, 100);
                }
            }, 100);
        }
    }

    // ✅ 기존 태그 데이터 로드 (중복 제거)
    if (selectedItem && selectedItem.tags && Array.isArray(selectedItem.tags)) {
        currentTags = selectedItem.tags.map(tagObj =>
            tagObj.name || tagObj.tagName || tagObj
        );
        renderTags(); // ✅ renderTags()만 호출
    } else {
        currentTags = [];
        renderTags(); // ✅ 빈 상태로 렌더링
    }
}

function hideCategorySelectArea() {
    const categoryArea = document.getElementById('categorySelectArea');
    const tagArea = document.getElementById('tagInputArea');

    if (categoryArea) {
        categoryArea.style.display = 'none';
    }

    if (tagArea) {
        tagArea.style.display = 'none';
    }

    resetCategoryAndTags();
}



function resetCategoryAndTags() {
    // 카테고리 선택 초기화
    const largeSelect = document.getElementById('largeCategorySelect');
    const mediumSelect = document.getElementById('mediumCategorySelect');
    const smallSelect = document.getElementById('smallCategorySelect');

    if (largeSelect) largeSelect.value = '';
    if (mediumSelect) {
        mediumSelect.value = '';
        mediumSelect.disabled = true;
        mediumSelect.innerHTML = '<option value="">중분류 선택</option>';
    }
    if (smallSelect) {
        smallSelect.value = '';
        smallSelect.disabled = true;
        smallSelect.innerHTML = '<option value="">소분류 선택</option>';
    }

    // 태그 초기화
    currentTags = [];
    const tagInput = document.getElementById('tagInput');
    const tagDisplay = document.getElementById('tagDisplay');

    if (tagInput) tagInput.value = '';
    if (tagDisplay) tagDisplay.innerHTML = '';
}

// ===== 전체선택 기능 =====
const selectAllBtn = document.getElementById('selectAllBtn');
let isAllSelected = false;

if (selectAllBtn) {
    selectAllBtn.addEventListener('click', function() {
        // ⭐ document.querySelectorAll로 모든 체크박스 선택 ⭐
        const checkboxes = document.querySelectorAll('.item-checkbox');
        isAllSelected = !isAllSelected;

        if (isAllSelected) {
            checkboxes.forEach(checkbox => {
                checkbox.checked = true;

                // ⭐ 가장 가까운 항목 찾기 (폴더 안 파일도 포함) ⭐
                const item = checkbox.closest('.file-item, .note-item, .folder-item');
                if (!item) return;

                item.classList.add('multi-selected');

                let itemData, type;

                // Note 항목
                if (item.classList.contains('note-item')) {
                    type = 'note';
                    const noteIdx = item.dataset.noteIdx;
                    itemData = itemsData.notes.find(n => n.noteIdx == noteIdx);

                    // ⭐ 폴더 안의 노트도 찾기 ⭐
                    if (!itemData) {
                        itemData = findNoteInFolders(itemsData.noteFolders, noteIdx);
                    }
                }
                // File 항목
                else if (item.classList.contains('file-item')) {
                    type = 'file';
                    const gridfsId = item.dataset.gridfsId;
                    itemData = itemsData.files.find(f => f.gridfsId == gridfsId);

                    // ⭐ 폴더 안의 파일도 찾기 ⭐
                    if (!itemData) {
                        itemData = findFileInFolders(itemsData.fileFolders, gridfsId);
                    }
                }
                // Folder 항목
                else if (item.classList.contains('folder-item')) {
                    const folderId = item.dataset.folderId;

                    if (currentTab === 'files') {
                        type = 'folder';
                        itemData = itemsData.fileFolders.find(f => f.id == folderId);
                    } else {
                        type = 'noteFolder';
                        itemData = itemsData.noteFolders.find(f => f.folderId == folderId);
                    }
                }

                if (itemData) {
                    const exists = selectedItems.some(si => {
                        if (type === 'note') return si.item.noteIdx == itemData.noteIdx;
                        if (type === 'file') return si.item.gridfsId == itemData.gridfsId;
                        if (type === 'folder') return si.item.id == itemData.id;
                        if (type === 'noteFolder') return si.item.folderId == itemData.folderId;
                        return false;
                    });

                    if (!exists) {
                        selectedItems.push({ item: itemData, el: item, type: type });
                    }
                }
            });

            selectAllBtn.textContent = '전체 해제';
        } else {
            clearMultiSelection();
            selectAllBtn.textContent = '전체 선택';
        }

        updateMultiSelectionUI();
    });
}

// ⭐ 폴더 안에서 노트 재귀적으로 찾기 ⭐
function findNoteInFolders(folders, noteIdx) {
    for (const folder of folders) {
        if (folder.notes) {
            const note = folder.notes.find(n => n.noteIdx == noteIdx);
            if (note) return note;
        }
        if (folder.subfolders) {
            const found = findNoteInFolders(folder.subfolders, noteIdx);
            if (found) return found;
        }
    }
    return null;
}

// ⭐ 폴더 안에서 파일 재귀적으로 찾기 ⭐
function findFileInFolders(folders, gridfsId) {
    for (const folder of folders) {
        if (folder.files) {
            const file = folder.files.find(f => f.gridfsId == gridfsId);
            if (file) return file;
        }
        if (folder.subfolders) {
            const found = findFileInFolders(folder.subfolders, gridfsId);
            if (found) return found;
        }
    }
    return null;
}


async function renderHWPFile(container, file) {
    try {
        container.innerHTML = '<div style="text-align:center;padding:40px;color:#666;">HWP 파일 로딩 중...</div>';

        console.log('====== HWP 파일 처리 시작 ======');
        console.log('파일명:', file.originalName);

        const res = await fetch(`/api/files/preview/${file.gridfsId || file.id}`, {
            method: 'GET',
            credentials: 'same-origin'
        });

        if (!res.ok) {
            throw new Error(`서버 응답 오류: ${res.status}`);
        }

        const blob = await res.blob();
        console.log('다운로드 완료:', blob.size, 'bytes');

        if (blob.size < 512) {
            throw new Error('파일을 제대로 받지 못했습니다.');
        }

        // ⭐ 파일 시그니처 확인 ⭐
        const arrayBuffer = await blob.arrayBuffer();
        const header = new Uint8Array(arrayBuffer.slice(0, 8));
        const signature = Array.from(header).map(b => b.toString(16).padStart(2, '0')).join(' ');
        console.log('파일 시그니처:', signature);

        // HWPX 체크 (ZIP 형식: 50 4B 03 04)
        if (header[0] === 0x50 && header[1] === 0x4B) {
            console.warn('⚠️ HWPX 형식은 지원하지 않습니다');
            showHWPError(container, file,
                'HWPX 파일은 웹 브라우저 미리보기를 지원하지 않습니다.\n' +
                'HWP 5.0 (구 형식)만 미리보기 가능합니다.');
            return;
        }

        // HWP 5.0만 처리
        if (!(header[0] === 0xD0 && header[1] === 0xCF)) {
            console.warn('⚠️ 알 수 없는 HWP 형식:', signature);
            showHWPError(container, file, '지원하지 않는 HWP 형식입니다.');
            return;
        }

        console.log('✅ HWP 5.0 형식 감지');

        const hwpFile = new File([blob], file.originalName);
        const reader = new FileReader();

        reader.onloadend = (e) => {
            try {
                const bstr = e.target.result;
                console.log('FileReader 완료, 길이:', bstr.length);

                container.innerHTML = '';
                new window.HWP.Viewer(container, bstr);

                // 렌더링 후 내용 체크
                setTimeout(() => {
                    const textContent = container.textContent || '';
                    const cleanText = textContent.replace(/[\s\n\r]/g, '');
                    console.log('텍스트 길이:', cleanText.length);

                    if (cleanText.length < 500) {
                        console.warn('⚠️ 텍스트 내용 부족');
                        showHWPError(container, file,
                            '이 HWP 파일은 텍스트가 거의 없거나 이미지/도형이 포함되어 파싱이 불가능합니다.');
                    } else {
                        console.log('✅ HWP 렌더링 성공!');
                    }
                }, 1000);

            } catch (err) {
                console.error('❌ HWP 렌더링 실패:', err);

                let errorMsg = '파일을 표시할 수 없습니다.';
                if (err.message.includes('FileHeader')) {
                    errorMsg = 'HWP 파일 형식이 손상되었거나 지원되지 않습니다.';
                }

                showHWPError(container, file, errorMsg);
            }
        };

        reader.onerror = () => {
            showHWPError(container, file, '파일을 읽을 수 없습니다.');
        };

        reader.readAsBinaryString(hwpFile);

    } catch (err) {
        console.error('❌ 전체 프로세스 실패:', err);
        showHWPError(container, file, err.message);
    }
}

function showHWPError(container, file, message) {
    container.innerHTML = `
        <div style="text-align:center;padding:60px 20px;">
            <div style="font-size:48px;margin-bottom:20px;">📄</div>
            <h3 style="margin:20px 0;color:#333;">${file.originalName}</h3>
            <p style="color:#ff9800;margin:30px 0;line-height:1.8;font-size:15px;">
                ⚠️ ${message}
            </p>
            <div style="display:flex;gap:15px;justify-content:center;margin-top:40px;">
                <button onclick="window.open('/api/files/download/${file.gridfsId || file.id}')" 
                        style="padding:14px 32px;background:#007bff;color:white;border:none;border-radius:8px;cursor:pointer;font-size:15px;font-weight:600;">
                    💾 다운로드하여 열기
                </button>
            </div>
            <p style="margin-top:30px;font-size:13px;color:#999;">
                💡 한글 프로그램이나 무료 뷰어에서 열어주세요
            </p>
        </div>
    `;
}


// ===== 선택 삭제 기능 =====
const bulkDeleteBtn = document.getElementById('bulkDeleteBtn');

if (bulkDeleteBtn) {
    bulkDeleteBtn.addEventListener('click', async function() {
        if (selectedItems.length === 0) {
            alert('삭제할 항목을 선택해주세요.');
            return;
        }

        if (!confirm(`선택한 ${selectedItems.length}개 항목을 삭제하시겠습니까?`)) {
            return;
        }

        const deletePromises = [];

        for (const si of selectedItems) {
            const { type, item } = si;


            if (type === 'file') {
                // ✅ 파일 삭제
                deletePromises.push(
                    secureFetch(`/api/files/delete/${item.gridfsId}`, {
                        method: 'DELETE'
                    })
                );
            } else if (type === 'note') {
                // ✅ 노트 삭제
                deletePromises.push(
                    secureFetch(`/notion/api/notion/${item.noteIdx}`, {
                        method: 'DELETE'
                    })
                );
            } else if (type === 'folder') {
                // ✅ 파일 폴더 삭제
                deletePromises.push(
                    secureFetch(`/api/unified/files/folder/${item.id}`, {
                        method: 'DELETE'
                    })
                );
            } else if (type === 'noteFolder') {
                // ✅ 노트 폴더 삭제
                deletePromises.push(
                    secureFetch(`/api/unified/notes/folder/${item.folderId}`, {
                        method: 'DELETE'
                    })
                );
            }
        }

        try {
            const results = await Promise.allSettled(deletePromises);

            const failed = results.filter(r => r.status === 'rejected');

            if (failed.length > 0) {
                console.error('삭제 실패:', failed);
                alert(`${selectedItems.length - failed.length}개 삭제 완료, ${failed.length}개 실패`);
            } else {
                alert('선택한 항목이 모두 삭제되었습니다.');
            }

            clearMultiSelection();
            isAllSelected = false;
            selectAllBtn.textContent = '🔲 전체선택';
            await fetchTreeData();

        } catch (error) {
            console.error('삭제 중 오류:', error);
            alert('삭제 중 오류가 발생했습니다.');
        }
    });



}

