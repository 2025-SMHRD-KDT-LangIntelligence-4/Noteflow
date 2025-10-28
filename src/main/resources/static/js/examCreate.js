document.addEventListener('DOMContentLoaded', () => {
    const itemList = document.getElementById('itemList');
    const selectedTagsContainer = document.getElementById('selectedTagsContainer');
    const createBtn = document.getElementById('createExamBtn');
    const examTitle = document.getElementById('examTitle');
    const keywordInput = document.getElementById('keywordInput');
    const addKeywordBtn = document.getElementById('addKeywordBtn');
    const clearAllKeywordsBtn = document.getElementById('clearAllKeywordsBtn');
    const questionCount = document.getElementById('questionCount');
    const questionHint = document.getElementById('questionHint');
    const searchInput = document.getElementById('searchInput');
    const clearSearchBtn = document.getElementById('clearSearchBtn');

    let selectedTags = new Set();
    let selectedNoteIdx = null;
    let folderTree = [];
    let rootNotes = [];
    try {
        folderTree = typeof FOLDER_TREE === 'string' ? JSON.parse(FOLDER_TREE) : FOLDER_TREE || [];
        rootNotes = typeof ROOT_NOTES === 'string' ? JSON.parse(ROOT_NOTES) : ROOT_NOTES || [];
    } catch (e) {
        console.error('JSON 파싱 오류:', e);
        folderTree = [];
        rootNotes = [];
    }
	renderTree();
    // 초기 렌더링
	/** 
    renderTree();
    if (PRESELECTED_NOTE_IDX) {
        setTimeout(() => {
            autoSelectNote(PRESELECTED_NOTE_IDX, PRESELECTED_KEYWORDS);
        }, 300);
    }
    
     * 트리 렌더링 (재귀)
     */
    function renderTree() {
        itemList.innerHTML = '';

        if (folderTree.length === 0 && rootNotes.length === 0) {
            itemList.innerHTML = '<p class="guide-text">노트를 선택하거나 직접 키워드를 추가하세요.</p>';
            return;
        }

        folderTree.forEach(folder => {
            renderFolder(folder, 0, itemList);
        });

        rootNotes.forEach(note => {
            const noteEl = createNoteElement(note, 0);
            itemList.appendChild(noteEl);
        });
    }

    /**
     * 폴더 재귀 렌더링
     */
    function renderFolder(folder, depth, parentEl) {
        const folderContainer = document.createElement('div');
        folderContainer.className = 'folder-container';

        const folderItem = document.createElement('div');
        folderItem.className = 'folder-item';
        folderItem.dataset.folderId = folder.folderId;

        

        const hasChildren = folder.subfolders.length > 0 || folder.notes.length > 0;

        folderItem.innerHTML = `
        <span class="folder-toggle ${hasChildren ? 'expanded' : 'empty'}">▼</span>
        <span class="item-icon">📁</span>
        <span class="folder-name" data-full-text="${escapeHtml(folder.folderName)}" title="${escapeHtml(folder.folderName)}">${escapeHtml(folder.folderName)}</span>
    `;

        folderContainer.appendChild(folderItem);

        if (hasChildren) {
            const folderChildren = document.createElement('div');
            folderChildren.className = 'folder-children expanded';

            folder.subfolders.forEach(subfolder => {
                renderFolder(subfolder, depth + 1, folderChildren);
            });

            folder.notes.forEach(note => {
                const noteEl = createNoteElement(note, depth + 1);
                folderChildren.appendChild(noteEl);
            });

            folderContainer.appendChild(folderChildren);
        }

        parentEl.appendChild(folderContainer);
    }


    /**
     * 노트 엘리먼트 생성
     */
	function createNoteElement(note, depth) {
	    const noteEl = document.createElement('div');
	    noteEl.className = 'note-item';
	    noteEl.dataset.noteIdx = note.noteIdx;
	    noteEl.dataset.tags = (note.tags || []).join(',');

	    // 아이콘
	    const icon = document.createElement('span');
	    icon.className = 'item-icon';
	    icon.innerHTML = '📝';

	    // 제목 래퍼 (overflow를 관리하기 위함)
	    const titleWrapper = document.createElement('div');
	    titleWrapper.className = 'note-title-wrapper';

	    const title = document.createElement('span');
	    title.className = 'note-title';
	    title.setAttribute('data-full-text', escapeHtml(note.title));
	    title.setAttribute('title', note.title); // 기존 툴팁 유지
	    title.textContent = note.title;

	    titleWrapper.appendChild(title);

	    // 조립
	    noteEl.appendChild(icon);
	    noteEl.appendChild(titleWrapper);

	    // --- 제목 hover 시 슬라이드 처리 (mouseenter / mouseleave 사용) ---
	    // mouseenter: 자식 요소로 들어갈 때 한 번만 실행됨 (버블 문제 줄임)
	    title.addEventListener('mouseenter', (e) => {
	        // wrapper와 title 실제 너비 측정
	        const wrapperWidth = titleWrapper.clientWidth;
	        const textWidth = title.scrollWidth;

	        // 제목이 wrapper보다 길 때만 작동
	        if (textWidth > wrapperWidth) {
	            const distance = textWidth - wrapperWidth;
	            // duration 산정: px당 시간 (예: 12ms) — 필요하면 수치 조정
	            const duration = Math.max(800, Math.round(distance * 12)); // 최소 800ms 보장

	            title.style.setProperty('--scroll-distance', `-${distance}px`);
	            title.style.setProperty('--scroll-duration', `${duration}ms`);
	            title.classList.add('scrolling');
	        }
	    });

	    // mouseleave: wrapper를 벗어날 때 초기화
	    title.addEventListener('mouseleave', (e) => {
	        title.classList.remove('scrolling');
	        title.style.transform = 'translateX(0)';
	        title.style.removeProperty('--scroll-distance');
	        title.style.removeProperty('--scroll-duration');
	    });

	    // (기존에서 노트 선택 등 추가 로직이 item click 핸들러로 처리되므로 여기선 단순 리턴)
	    return noteEl;
	}


    /**
     * HTML 이스케이프 (XSS 방지)
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * 폴더 토글
     */
    itemList.addEventListener('click', (e) => {
        // 폴더 토글 처리
        if (e.target.classList.contains('folder-toggle')) {
            e.stopPropagation();

            if (e.target.classList.contains('empty')) return;

            const folderItem = e.target.closest('.folder-item');
            const container = folderItem.closest('.folder-container');
            const children = container.querySelector('.folder-children');
            const toggle = e.target;

            if (children.classList.contains('expanded')) {
                children.classList.remove('expanded');
                toggle.classList.remove('expanded');
                toggle.textContent = '▶';
            } else {
                children.classList.add('expanded');
                toggle.classList.add('expanded');
                toggle.textContent = '▼';
            }
            return;
        }

        // 노트 선택
        if (e.target.closest('.note-item')) {
            const noteEl = e.target.closest('.note-item');
            const isCtrlPressed = e.ctrlKey || e.metaKey; // Windows: Ctrl, Mac: Cmd

            // Ctrl 클릭이 아니면 기존 선택 해제
            if (!isCtrlPressed) {
                document.querySelectorAll('.note-item.selected').forEach(el => {
                    el.classList.remove('selected');
                });
                selectedTags.clear();
                selectedNoteIdx = null;
            }

            // 현재 노트 선택 토글
            if (noteEl.classList.contains('selected') && isCtrlPressed) {
                // Ctrl+클릭으로 선택 해제
                noteEl.classList.remove('selected');

                // 해당 노트의 태그만 제거
                const tags = noteEl.dataset.tags.split(',').filter(t => t.trim());
                tags.forEach(tag => selectedTags.delete(tag.trim()));
            } else {
                // 노트 선택
                noteEl.classList.add('selected');
                selectedNoteIdx = noteEl.dataset.noteIdx;

                // 태그 추가 (Ctrl 클릭이면 누적, 일반 클릭이면 새로 시작)
                const tags = noteEl.dataset.tags.split(',').filter(t => t.trim());
                tags.forEach(tag => selectedTags.add(tag.trim()));
            }

            displaySelectedTags();
            generateAutoTitle();
            updateAutoQuestionCount();
            validateForm();
            updateClearAllButton();
        }
    });
    /**
     * 전체 삭제 버튼
     */
    clearAllKeywordsBtn.addEventListener('click', () => {
        if (selectedTags.size === 0) return;

        if (confirm('모든 키워드를 삭제하시겠습니까?')) {
            selectedTags.clear();
            selectedNoteIdx = null;

            // 선택된 노트 해제
            document.querySelectorAll('.note-item.selected').forEach(el => {
                el.classList.remove('selected');
            });

            displaySelectedTags();
            generateAutoTitle();
            updateAutoQuestionCount();
            validateForm();
            updateClearAllButton();
        }
    });

    /**
     * 전체 삭제 버튼 상태 업데이트
     */
    function updateClearAllButton() {
        if (selectedTags.size === 0) {
            clearAllKeywordsBtn.disabled = true;
            clearAllKeywordsBtn.style.opacity = '0.5';
        } else {
            clearAllKeywordsBtn.disabled = false;
            clearAllKeywordsBtn.style.opacity = '1';
        }
    }

    // 검색 기능
    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase();

        if (query) {
            clearSearchBtn.style.display = 'block';
            filterNotes(query);
        } else {
            clearSearchBtn.style.display = 'none';
            renderTree();
        }
    });

    clearSearchBtn.addEventListener('click', () => {
        searchInput.value = '';
        clearSearchBtn.style.display = 'none';
        renderTree();
    });

    function filterNotes(query) {
        const allNotes = document.querySelectorAll('.note-item');
        allNotes.forEach(noteEl => {
            const title = noteEl.querySelector('.note-title').textContent.toLowerCase();
            if (title.includes(query)) {
                noteEl.style.display = 'flex';
            } else {
                noteEl.style.display = 'none';
            }
        });
    }

    if (PRESELECTED_NOTE_IDX && PRESELECTED_NOTE_IDX !== 'null') {
        setTimeout(() => {
            autoSelectNote(PRESELECTED_NOTE_IDX, PRESELECTED_KEYWORDS);
        }, 300);
    }

    /**
     * 노트 자동 선택
     */
    function autoSelectNote(noteIdx, keywords) {
        // 노트 찾기
        const noteEl = document.querySelector(`.note-item[data-note-idx="${noteIdx}"]`);

        if (noteEl) {
            // 노트 선택
            noteEl.click();

            // 키워드 추가
            if (keywords && Array.isArray(keywords)) {
                keywords.forEach(keyword => {
                    selectedTags.add(keyword.trim());
                });
                displaySelectedTags();
                generateAutoTitle();
                updateAutoQuestionCount();
                validateForm();
            }

            // 노트 위치로 스크롤
            noteEl.scrollIntoView({ behavior: 'smooth', block: 'center' });

            // 알림
            setTimeout(() => {
                alert('✅ 노트 정보가 자동으로 적용되었습니다!');
            }, 500);
        }
    }

    // 문제 개수 모드 전환
    document.querySelectorAll('input[name="questionMode"]').forEach(radio => {
        radio.addEventListener('change', (e) => {
            if (e.target.value === 'auto') {
                questionCount.disabled = true;
                updateAutoQuestionCount();
            } else {
                questionCount.disabled = false;
                questionHint.textContent = '5~100개 사이로 설정하세요';
            }
        });
    });

    function updateAutoQuestionCount() {
        const mode = document.querySelector('input[name="questionMode"]:checked').value;
        if (mode === 'auto') {
            const count = selectedTags.size * 10;
            questionCount.value = count > 0 ? count : 10;
            questionHint.textContent = `키워드 ${selectedTags.size}개 × 10문제 = ${questionCount.value}문제`;
        }
    }

    function generateAutoTitle() {
        if (selectedTags.size === 0) {
            examTitle.placeholder = '키워드를 선택하면 자동 생성됩니다';
            return;
        }

        const today = new Date();
        const dateStr = `${today.getMonth() + 1}월 ${today.getDate()}일`;

        const keywords = Array.from(selectedTags).slice(0, 3);
        const keywordStr = keywords.join(', ');

        examTitle.value = `${keywordStr} 테스트 (${dateStr})`;
    }

    keywordInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addKeyword();
        }
    });

    addKeywordBtn.addEventListener('click', addKeyword);

    function addKeyword() {
        const keyword = keywordInput.value.trim();
        if (!keyword) return;

        selectedTags.add(keyword);
        keywordInput.value = '';

        displaySelectedTags();
        generateAutoTitle();
        updateAutoQuestionCount();
        validateForm();
        updateClearAllButton();
    }

    function displaySelectedTags() {
        if (selectedTags.size === 0) {
            selectedTagsContainer.innerHTML = '<p class="empty-text">노트 선택 또는 직접 키워드 입력</p>';
            updateClearAllButton();
            return;
        }

        selectedTagsContainer.innerHTML = Array.from(selectedTags)
            .map(tag => `
                <span class="selected-tag">
                    ${tag}
                    <button class="tag-remove-btn" data-tag="${tag}">✕</button>
                </span>
            `)
            .join('');

        // 태그 제거 이벤트
        selectedTagsContainer.querySelectorAll('.tag-remove-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const tag = e.target.dataset.tag;
                selectedTags.delete(tag);
                displaySelectedTags();
                generateAutoTitle();
                updateAutoQuestionCount();
                validateForm();
                updateClearAllButton();
            });
        });

        updateClearAllButton();
    }

    function validateForm() {
        const isValid = selectedTags.size > 0;
        createBtn.disabled = !isValid;
    }

    // 시험 생성
    createBtn.addEventListener('click', async () => {
        if (!examTitle.value.trim()) {
            generateAutoTitle();
        }

        const mode = document.querySelector('input[name="questionMode"]:checked').value;
        const finalQuestionCount = mode === 'auto' ?
            selectedTags.size * 20 :
            parseInt(questionCount.value);

        const requestData = {
            noteIdx: selectedNoteIdx,
            keywords: Array.from(selectedTags),
            title: examTitle.value.trim(),
            questionCount: finalQuestionCount,
            difficulty: document.getElementById('difficulty').value || null,
            scorePerQuestion: parseInt(document.getElementById('scorePerQuestion').value),
            adaptiveDifficulty: document.getElementById('difficulty').value === ''
        };


        createBtn.disabled = true;
        createBtn.textContent = '🔄 생성 중...';

        try {
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

            const headers = {
                'Content-Type': 'application/json'
            };

            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken;
            }

            const response = await fetch('/exam/api/create-from-keywords', {
                method: 'POST',
                headers: headers,
                credentials: 'include',
                body: JSON.stringify(requestData)
            });

            const result = await response.json();

            if (result.success) {
                alert('✅ 시험이 생성되었습니다!');
                window.location.href = `/exam/solve/${result.testIdx}`;
            } else {
                alert('❌ 시험 생성 실패: ' + result.message);
                createBtn.disabled = false;
                createBtn.textContent = '🎯 시험 생성하기';
            }

        } catch (error) {
            console.error('시험 생성 오류:', error);
            alert('❌ 시험 생성 중 오류가 발생했습니다.');
            createBtn.disabled = false;
            createBtn.textContent = '🎯 시험 생성하기';
        }
    });

    examTitle.placeholder = '자동 생성됩니다 (수정 가능)';
	// ========== 노트 제목 슬라이드 효과 추가 ==========
	document.addEventListener('mouseover', (e) => {
	    const title = e.target.closest('.note-title');
	    if (!title) return;

	    const wrapperWidth = title.parentElement.clientWidth;
	    const textWidth = title.scrollWidth;

	    // 제목이 wrapper보다 길 때만 슬라이드
	    if (textWidth > wrapperWidth) {
	        const distance = textWidth - wrapperWidth;
	        const duration = distance * 15; // px당 속도
	        title.style.setProperty('--scroll-distance', `-${distance}px`);
	        title.style.setProperty('--scroll-duration', `${duration}ms`);
	        title.classList.add('scrolling');
	    }
	});

	document.addEventListener('mouseout', (e) => {
	    const title = e.target.closest('.note-title');
	    if (!title) return;
	    title.classList.remove('scrolling');
	    title.style.transform = 'translateX(0)';
	});

});
