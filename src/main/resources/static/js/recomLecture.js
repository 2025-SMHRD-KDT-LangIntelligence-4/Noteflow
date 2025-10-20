
(function() {
	const API = '/lecture/api/recommend';

	document.addEventListener('DOMContentLoaded', () => {
		// 1) NotionComplete에서 넘어온 payload가 있으면 자동 검색
		const payloadStr = sessionStorage.getItem('lectureRecommendPayload');
		if (payloadStr) {
			try {
				const payload = JSON.parse(payloadStr);
				// 사용 후 클리어(뒤로가기 방지 원하면 지우지 말고 유지)
				sessionStorage.removeItem('lectureRecommendPayload');
				postRecommend(payload);
			} catch (_) {
				// 파싱 실패시 무시
			}
		}
		// 2) 직접 진입한 경우: 초기에는 아무 것도 안 불러옴(요구사항)
		// 3) 검색 버튼으로 수동 요청
		const btn = document.getElementById('rec-search-btn');
		const input = document.getElementById('rec-search');
		if (btn && input) {
			btn.addEventListener('click', () => {
				const text = input.value.trim();
				// #태그가 포함되면 태그 검색, 아니면 제목 키워드로 처리
				const hasHash = /#\S/.test(text);
				if (hasHash) {
					const tags = text.split(/[,\s]+/)
						.map(s => s.replace(/^#/, '').trim())
						.filter(Boolean);
					postRecommend({ tags, like: false, size: 30 });
				} else if (text) {
					postRecommend({ keyword: text, size: 30 });
				} else {
					// 아무 값 없으면 빈 상태 유지
					clearList();
				}
			});
		}
	});

	async function postRecommend(payload) {
		const listEl = document.getElementById('rec-lecture-list');
		const emptyEl = document.getElementById('rec-empty');
		const errorEl = document.getElementById('rec-error');

		listEl.innerHTML = '';
		emptyEl.style.display = 'none';
		errorEl.style.display = 'none';

		try {
			const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
			// CSRF
			const csrf = document.querySelector('meta[name="_csrf"]');
			const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
			if (csrf && csrfHeader) {
				headers[csrfHeader.getAttribute('content')] = csrf.getAttribute('content');
			}

			const res = await fetch(API, {
				method: 'POST',
				headers,
				credentials: 'include',
				body: JSON.stringify(payload || {})
			});
			if (!res.ok) throw new Error('HTTP ' + res.status);
			const { success, items } = await res.json();

			if (!success || !Array.isArray(items) || items.length === 0) {
				emptyEl.style.display = 'block';
				return;
			}
			renderCards(normalize(items));
		} catch (e) {
			console.error('[recomLecture] POST error:', e);
			errorEl.style.display = 'block';
		}
	}

	function clearList() {
		const listEl = document.getElementById('rec-lecture-list');
		const emptyEl = document.getElementById('rec-empty');
		const errorEl = document.getElementById('rec-error');
		listEl.innerHTML = '';
		errorEl.style.display = 'none';
		emptyEl.style.display = 'none';
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
		const frag = document.createDocumentFragment();

		items.forEach(it => {
			const node = tpl.content.cloneNode(true);
			const titleEl = node.querySelector('.rec-title');
			const tagsEl = node.querySelector('.rec-tags');
			const btnEl = node.querySelector('.rec-openBtn');

			titleEl.textContent = it.title;
			tagsEl.textContent = buildTagsLine(it.tags, it.small, it.medium, it.large);
			btnEl.addEventListener('click', () => { if (it.url) window.open(it.url, '_blank', 'noopener'); });

			// 검색 필터 확장 시 대비: 카드별 검색 blob 저장
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
})();
