(function() {

	const API = '/lecture/api/recommend';
	let currentTags = [];
	let allLectures = []; // 전체 검색 결과
	let currentPage = 1;
	const itemsPerPage = 15; // 페이지당 15개
	// ⭐ 오디오 증폭 관련 변수
	let audioContext = null;
	let gainNode = null;
	let analyser = null;
	let source = null;
	let isManualControl = false;  // ⭐ 수동 조절 중인지 플래그
	let manualControlTimeout = null;
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

		//  검색 모드 가져오기
		const searchMode = document.querySelector('input[name="searchMode"]:checked')?.value || 'OR';

		const payload = {
			keyword: currentTags.join(', '),
			tags: currentTags,  // 배열로도 전달
			searchMode: searchMode,  // ⭐ OR 또는 AND ⭐
			size: 1000
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
// 버튼 변경 시 자동 재검색
	const searchModeRadios = document.querySelectorAll('input[name="searchMode"]');
	searchModeRadios.forEach(radio => {
		radio.addEventListener('change', () => {
			if (currentTags.length > 0) {
				console.log(`🔄 검색 모드 변경: ${radio.value}`);
				performSearch();  // 즉시 재검색
			}
		});
	});


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
			small: d.categorySmall ?? d.category_small ?? null,
			videoFileId: d.videoFileId ?? d.video_file_id ?? '',
			hasOfflineVideo: d.hasOfflineVideo ?? false
		}));
	}

	function renderCards(items) {
		const listEl = document.getElementById('rec-lecture-list');
		const tpl = document.getElementById('rec-lecture-card-tpl');
		if (!listEl || !tpl) return;

		listEl.innerHTML = '';
		const frag = document.createDocumentFragment();

		items.forEach(it => {
			const node = tpl.content.cloneNode(true);

			const titleEl = node.querySelector('.rec-title');
			const tagsEl = node.querySelector('.rec-tags');
			const btnEl = node.querySelector('.rec-openBtn');
			const btn2El = node.querySelector('.rec-openBtn2');

			titleEl.textContent = it.title;
			tagsEl.textContent = buildTagsLine(it.tags, it.small, it.medium, it.large);

			// 강의열기 버튼
			btnEl.addEventListener('click', () => {
				if (it.url) window.open(it.url, '_blank', 'noopener');
			});

			// ⭐ 바로보기 버튼 처리
			if (btn2El) {
				const canPlayOffline = checkCanPlayOffline(it);

				if (canPlayOffline) {
					btn2El.disabled = false;
					btn2El.classList.remove('disabled');
					btn2El.classList.add('enabled');

					// ⭐ 클릭 이벤트 등록 (중요!)
					btn2El.addEventListener('click', () => {
						console.log(`[클릭] videoFileId: ${it.videoFileId}`);
						playOfflineVideo(it.videoFileId, it.title);
					});
				} else {
					btn2El.disabled = true;
					btn2El.classList.add('disabled');
					btn2El.classList.remove('enabled');

					// disabled 버튼 클릭 시 알림
					btn2El.addEventListener('click', (e) => {
						e.preventDefault();
						alert('이 강의는 오프라인 재생을 지원하지 않습니다.');
					});
				}

				console.log(`[${it.title}] 바로보기: ${canPlayOffline ? '활성화' : '비활성화'} (videoFileId: ${it.videoFileId})`);
			}

			node.querySelector('.lecture-list').dataset.searchBlob =
				(it.title + ' ' + tagsEl.textContent).toLowerCase();

			frag.appendChild(node);
		});

		listEl.appendChild(frag);
	}
	function checkCanPlayOffline(lecture) {
		const hasVideo = lecture.videoFileId && lecture.videoFileId.trim() !== '';
		if (!hasVideo) {
			return false;
		}

		const isSmhrd = lecture.title && lecture.title.includes('[스마트인재개발원]');
		const hasSingaeTag = lecture.tags && lecture.tags.some(tag =>
			tag === '스인개' || tag === '스마트인재개발원'
		);

		return hasVideo && (isSmhrd || hasSingaeTag);
	}

	// ⭐ 오프라인 영상 재생
	function playOfflineVideo(videoFileId, title) {
		if (!videoFileId) {
			alert('재생 가능한 영상이 없습니다.');
			return;
		}

		console.log(`[재생 시작] videoFileId: ${videoFileId}, title: ${title}`);

		const modal = document.getElementById('videoModal');
		const modalTitle = document.getElementById('videoModalTitle');
		const videoSource = document.getElementById('videoSource');
		const videoPlayer = document.getElementById('videoPlayer');

		if (!modal || !modalTitle || !videoSource || !videoPlayer) {
			console.error('모달 요소를 찾을 수 없습니다!');
			alert('비디오 플레이어를 초기화할 수 없습니다.');
			return;
		}

		modalTitle.textContent = title;
		videoSource.src = `/api/video/stream/${videoFileId}`;

		videoPlayer.load();
		modal.style.display = 'block';

		// ⭐ 오디오 증폭 설정
		setupAudioBoost(videoPlayer);

		videoPlayer.play().catch(err => {
			console.error('자동 재생 실패:', err);
		});
	}

// ⭐ 오디오 자동 증폭 설정
	function setupAudioBoost(videoElement) {
		try {
			// 이미 설정되어 있으면 초기화
			if (audioContext) {
				audioContext.close();
			}

			// Web Audio API 초기화
			audioContext = new (window.AudioContext || window.webkitAudioContext)();

			// 비디오 소스 연결
			source = audioContext.createMediaElementSource(videoElement);

			// 게인 노드 생성 (초기 볼륨 1.0 = 100%)
			gainNode = audioContext.createGain();
			gainNode.gain.value = 1.0;

			// 분석기 노드 생성
			analyser = audioContext.createAnalyser();
			analyser.fftSize = 2048;

			// 노드 연결: 비디오 → 게인 → 분석기 → 스피커
			source.connect(gainNode);
			gainNode.connect(analyser);
			analyser.connect(audioContext.destination);

			console.log('[오디오 증폭] 초기화 완료');

			// 1초마다 음량 분석 및 자동 조절
			startVolumeAnalysis();

		} catch (e) {
			console.error('[오디오 증폭] 초기화 실패:', e);
		}
	}

// ⭐ 음량 자동 분석 및 조절
	function startVolumeAnalysis() {
		const bufferLength = analyser.frequencyBinCount;
		const dataArray = new Uint8Array(bufferLength);

		let analysisInterval = setInterval(() => {
			if (!analyser || !gainNode) {
				clearInterval(analysisInterval);
				return;
			}

			// ⭐ 수동 조절 중이면 자동 증폭 스킵
			if (isManualControl) {
				return;
			}

			// 현재 음량 분석
			analyser.getByteFrequencyData(dataArray);

			let sum = 0;
			for (let i = 0; i < bufferLength; i++) {
				sum += dataArray[i];
			}
			let average = sum / bufferLength;

			// 자동 증폭 로직
			let targetGain = 1.0;

			if (average < 30) {
				targetGain = 3.0;
			} else if (average < 50) {
				targetGain = 2;
			} else if (average < 80) {
				targetGain = 1.5;
			} else {
				targetGain = 1.0;
			}

			// 부드럽게 볼륨 조절
			if (audioContext) {
				gainNode.gain.linearRampToValueAtTime(
					targetGain,
					audioContext.currentTime + 0.5
				);

				// 슬라이더도 동기화
				const slider = document.getElementById('volumeBoost');
				const label = document.getElementById('volumeLabel');
				if (slider && label) {
					slider.value = targetGain.toFixed(1);
					label.textContent = targetGain.toFixed(1) + 'x';
				}
			}

		}, 5000); // ⭐ 5초마다로 변경 (1초는 너무 자주)

		window.audioAnalysisInterval = analysisInterval;
	}
	// ⭐ 수동 볼륨 조절
	function adjustVolume(value) {
		if (gainNode) {
			// 수동 조절 모드 활성화
			isManualControl = true;

			// 즉시 볼륨 적용
			gainNode.gain.setValueAtTime(parseFloat(value), audioContext.currentTime);
			document.getElementById('volumeLabel').textContent = value + 'x';
			console.log('[수동 볼륨 조절] ' + value + 'x');

			// 5초 동안 손 안 대면 자동 증폭 재개
			if (manualControlTimeout) {
				clearTimeout(manualControlTimeout);
			}
			manualControlTimeout = setTimeout(() => {
				isManualControl = false;
				console.log('[자동 증폭 재개]');
			}, 5000);
		} else {
			console.warn('[볼륨 조절] gainNode가 아직 초기화되지 않았습니다.');
		}
	}

	// ⭐ 모달 닫기
	function closeVideoModal() {
		const videoPlayer = document.getElementById('videoPlayer');
		const modal = document.getElementById('videoModal');

		if (videoPlayer) {
			videoPlayer.pause();
			videoPlayer.currentTime = 0;
		}

		// 타이머 정리
		if (window.audioAnalysisInterval) {
			clearInterval(window.audioAnalysisInterval);
			window.audioAnalysisInterval = null;
		}

		if (manualControlTimeout) {
			clearTimeout(manualControlTimeout);
			manualControlTimeout = null;
		}

		// 오디오 컨텍스트 정리
		if (audioContext) {
			audioContext.close();
			audioContext = null;
		}

		// 플래그 초기화
		isManualControl = false;

		if (modal) {
			modal.style.display = 'none';
		}
	}

	// 전역 함수로 등록 (HTML에서 onclick으로 호출)
	window.playOfflineVideo = playOfflineVideo;
	window.closeVideoModal = closeVideoModal;
	window.adjustVolume = adjustVolume;
	// ESC 키로 모달 닫기
	document.addEventListener('keydown', function(e) {
		if (e.key === 'Escape') {
			const modal = document.getElementById('videoModal');
			if (modal && modal.style.display === 'block') {
				closeVideoModal();
			}
		}
	});

	// 모달 배경 클릭 시 닫기
	document.addEventListener('DOMContentLoaded', function() {
		const modal = document.getElementById('videoModal');
		if (modal) {
			modal.addEventListener('click', function(e) {
				if (e.target === this) {
					closeVideoModal();
				}
			});
		}
	});

	function buildTagsLine(tags, small, medium, large) {
		const base = Array.isArray(tags) && tags.length ? tags : [small, medium, large].filter(Boolean);
		const uniq = Array.from(new Set(base.map(s => String(s).trim()).filter(Boolean)));
		return uniq.map(s => (s.startsWith('#') ? s : '#' + s)).join(' ');
	}




// ⭐ 추가: 모달 배경 클릭 시 닫기
	document.addEventListener('DOMContentLoaded', function() {
		const modal = document.getElementById('videoModal');
		if (modal) {
			modal.addEventListener('click', function(e) {
				if (e.target === this) {
					closeVideoModal();
				}
			});
		}
	});




	function escapeHtml(str) {
		if (!str) return '';
		const div = document.createElement('div');
		div.textContent = str;
		return div.innerHTML;
	}

})();
