(function() {

	const API = '/lecture/api/recommend';
	let currentTags = [];
	let allLectures = []; // 전체 검색 결과
	let currentPage = 1;
	const itemsPerPage = 15; // 페이지당 15개

	document.addEventListener('DOMContentLoaded', () => {

		// 1) NotionComplete에서 넘어온 payload
		const payloadStr = sessionStorage.getItem('lectureRecommendPayload');
		if (payloadStr) {
			try {
				const payload = JSON.parse(payloadStr);
				sessionStorage.removeItem('lectureRecommendPayload');

				if (payload.keyword) {
					currentTags = payload.keyword.split(',').map(s => s.trim()).filter(Boolean);
				} else if (payload.tags) {
					currentTags = payload.tags;
				}

				renderTagBubbles();
				performSearch();
			} catch (_) {}
		}

		// 2) 검색 버튼
		const btn = document.getElementById('rec-search-btn');
		const input = document.getElementById('rec-search');

		if (btn && input) {
			const executeSearch = () => {
				const text = input.value.trim();
				if (!text) return;

				const newTags = text.split(',').map(s => s.trim()).filter(Boolean);
				newTags.forEach(tag => {
					if (!currentTags.includes(tag)) {
						currentTags.push(tag);
					}
				});

				input.value = '';
				renderTagBubbles();
				performSearch();
			};

			btn.addEventListener('click', executeSearch);

			input.addEventListener('keypress', (e) => {
				if (e.key === 'Enter') {
					e.preventDefault();
					executeSearch();
				}
			});
		}

		// 3) 모두 지우기 버튼
		const clearBtn = document.getElementById('clear-tags-btn');
		if (clearBtn) {
			clearBtn.addEventListener('click', () => {
				currentTags = [];
				renderTagBubbles();
				clearList();
			});
		}

		// 4) 페이징 버튼
		const prevBtn = document.getElementById('prev-page-btn');
		const nextBtn = document.getElementById('next-page-btn');

		if (prevBtn) {
			prevBtn.addEventListener('click', () => {
				if (currentPage > 1) {
					currentPage--;
					renderCurrentPage();
					updatePaginationUI();
					scrollToTop();
				}
			});
		}

		if (nextBtn) {
			nextBtn.addEventListener('click', () => {
				const totalPages = Math.ceil(allLectures.length / itemsPerPage);
				if (currentPage < totalPages) {
					currentPage++;
					renderCurrentPage();
					updatePaginationUI();
					scrollToTop();
				}
			});
		}

	});

// 태그 버블 렌더링
	function renderTagBubbles() {
		const container = document.getElementById('tag-bubble-container');
		const clearBtn = document.getElementById('clear-tags-btn');

		if (!container) return;

		container.innerHTML = '';

		if (currentTags.length === 0) {
			container.innerHTML = '<span style="color:#999;font-size:13px;">태그를 추가하려면 검색어를 입력하세요</span>';
			if (clearBtn) clearBtn.style.display = 'none';
			return;
		}

		currentTags.forEach(tag => {
			const bubble = document.createElement('div');
			bubble.className = 'tag-bubble';
			bubble.innerHTML = `
            <span>${escapeHtml(tag)}</span>
            <span class="tag-bubble-remove">×</span>
        `;

			bubble.addEventListener('click', () => {
				removeTag(tag);
			});

			container.appendChild(bubble);
		});

		if (clearBtn) {
			clearBtn.style.display = currentTags.length > 0 ? 'inline-block' : 'none';
		}
	}

	function removeTag(tag) {
		currentTags = currentTags.filter(t => t !== tag);
		renderTagBubbles();

		if (currentTags.length === 0) {
			clearList();
		} else {
			performSearch();
		}
	}

// 검색 수행
	async function performSearch() {
		if (currentTags.length === 0) {
			clearList();
			return;
		}

		const payload = {
			keyword: currentTags.join(', '),
			size: 1000 // 전체 가져오기
		};

		await postRecommend(payload);
	}

	async function postRecommend(payload) {
		const emptyEl = document.getElementById('rec-empty');
		const errorEl = document.getElementById('rec-error');
		const paginationEl = document.getElementById('pagination-container');

		clearList();
		emptyEl.style.display = 'none';
		errorEl.style.display = 'none';
		paginationEl.style.display = 'none';

		console.log('[검색 요청]', payload);

		try {
			const headers = {
				'Content-Type': 'application/json',
				'Accept': 'application/json'
			};

			const csrf = document.querySelector('meta[name="csrf"]');
			const csrfHeader = document.querySelector('meta[name="csrfheader"]');
			if (csrf && csrfHeader) {
				headers[csrfHeader.getAttribute('content')] = csrf.getAttribute('content');
			}

			const res = await fetch(API, {
				method: 'POST',
				headers,
				credentials: 'include',
				body: JSON.stringify(payload)
			});

			console.log('[응답 상태]', res.status);

			if (!res.ok) {
				throw new Error(`HTTP ${res.status}`);
			}

			const result = await res.json();
			console.log('[검색 응답]', result);

			const { success, items, count } = result;

			if (!success || !Array.isArray(items) || items.length === 0) {
				emptyEl.style.display = 'block';
				return;
			}

			console.log(`✅ ${count}개 강의 발견`);

			// 전체 결과 저장
			allLectures = normalize(items);
			currentPage = 1;

			// 첫 페이지 렌더링
			renderCurrentPage();
			updatePaginationUI();

			// 페이징 표시
			if (allLectures.length > itemsPerPage) {
				paginationEl.style.display = 'flex';
			}

		} catch (e) {
			console.error('[검색 에러]', e);
			errorEl.style.display = 'block';
		}
	}

// 현재 페이지 렌더링
	function renderCurrentPage() {
		const startIdx = (currentPage - 1) * itemsPerPage;
		const endIdx = startIdx + itemsPerPage;
		const pageItems = allLectures.slice(startIdx, endIdx);

		renderCards(pageItems);
	}

// 페이징 UI 업데이트
	function updatePaginationUI() {
		const totalPages = Math.ceil(allLectures.length / itemsPerPage);

		const prevBtn = document.getElementById('prev-page-btn');
		const nextBtn = document.getElementById('next-page-btn');
		const pageInfo = document.getElementById('page-info');

		if (prevBtn) prevBtn.disabled = currentPage === 1;
		if (nextBtn) nextBtn.disabled = currentPage >= totalPages;
		if (pageInfo) pageInfo.textContent = `${currentPage} / ${totalPages}`;
	}

// 스크롤 맨 위로
	function scrollToTop() {
		const searchBar = document.getElementById('rec-search');
		if (searchBar) {
			// 검색창 위치로 스크롤 + 헤더 높이만큼 여유 (100px)
			const topPosition = searchBar.getBoundingClientRect().top + window.pageYOffset - 120;
			window.scrollTo({
				top: topPosition,
				behavior: 'smooth'
			});
		} else {
			// fallback: 페이지 맨 위
			window.scrollTo({ top: 0, behavior: 'smooth' });
		}
	}

	function clearList() {
		const listEl = document.getElementById('rec-lecture-list');
		const emptyEl = document.getElementById('rec-empty');
		const errorEl = document.getElementById('rec-error');
		const paginationEl = document.getElementById('pagination-container');

		if (listEl) listEl.innerHTML = '';
		if (errorEl) errorEl.style.display = 'none';
		if (emptyEl) emptyEl.style.display = 'none';
		if (paginationEl) paginationEl.style.display = 'none';

		allLectures = [];
		currentPage = 1;
	}

	function normalize(items) {
		return items.map(d => ({
			lecIdx: d.lecIdx ?? d.lec_idx ?? d.id,
			title: d.title ?? d.lecTitle ?? d.lec_title ?? '(제목 없음)',
			url: d.url ?? d.lecUrl ?? d.lec_url ?? '',
			tags: Array.isArray(d.tags) ? d.tags : [],
			large: d.categoryLarge ?? d.category_large ?? null,
			medium: d.categoryMedium ?? d.category_medium ?? null,
			small: d.categorySmall ?? d.category_small ?? null
		}));
	}

	function renderCards(items) {
		const listEl = document.getElementById('rec-lecture-list');
		const tpl = document.getElementById('rec-lecture-card-tpl');

		if (!listEl || !tpl) return;

		listEl.innerHTML = ''; // 기존 리스트 클리어
		const frag = document.createDocumentFragment();

		items.forEach(it => {
			const node = tpl.content.cloneNode(true);

			const titleEl = node.querySelector('.rec-title');
			const tagsEl = node.querySelector('.rec-tags');
			const btnEl = node.querySelector('.rec-openBtn');

			titleEl.textContent = it.title;
			tagsEl.textContent = buildTagsLine(it.tags, it.small, it.medium, it.large);

			btnEl.addEventListener('click', () => {
				if (it.url) window.open(it.url, '_blank', 'noopener');
			});

			node.querySelector('.lecture-list').dataset.searchBlob =
				(it.title + ' ' + tagsEl.textContent).toLowerCase();

			frag.appendChild(node);
		});

		listEl.appendChild(frag);
	}

	function buildTagsLine(tags, small, medium, large) {
		const base = Array.isArray(tags) && tags.length ? tags : [small, medium, large].filter(Boolean);
		const uniq = Array.from(new Set(base.map(s => String(s).trim()).filter(Boolean)));
		return uniq.map(s => (s.startsWith('#') ? s : '#' + s)).join(' ');
	}

	function escapeHtml(str) {
		if (!str) return '';
		const div = document.createElement('div');
		div.textContent = str;
		return div.innerHTML;
	}

})();
