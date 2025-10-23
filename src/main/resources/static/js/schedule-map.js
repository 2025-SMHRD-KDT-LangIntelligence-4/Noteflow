// /Noteflow/src/main/resources/static/js/schedule-map.js (신규 파일)

/**
 * 카카오맵 모달을 열고 주소 선택 기능을 제공합니다.
 * @param {HTMLInputElement} targetLocationInput - 주소 값을 받을 input 요소 (예: qaLocation, editLocation)
 */
// --- Kakao SDK Loader (중복/상태 불문 견고하게) ---
let kakaoReadyPromise = null;
function loadKakaoSdkOnce() {
	// 이미 로드된 경우
	if (window.kakao && window.kakao.maps) return Promise.resolve();

	if (kakaoReadyPromise) return kakaoReadyPromise;

	kakaoReadyPromise = new Promise((resolve, reject) => {
		// 페이지에 기존 스크립트가 있으면 그대로 load만 호출
		const already = Array.from(document.scripts).some(s => s.src.includes('dapi.kakao.com/v2/maps/sdk.js'));
		if (already && window.kakao && window.kakao.maps) {
			try { window.kakao.maps.load(resolve); } catch { resolve(); }
			return;
		}

		const s = document.createElement('script');
		// ★ HTTPS 명시 (일부 환경에서 // 로 시작하면 막히는 경우 방지)
		s.src = "https://dapi.kakao.com/v2/maps/sdk.js?appkey=ce4cb024a258c2c1af42941d2f17762e&libraries=services,clusterer,drawing&autoload=false";
		s.async = true;
		s.onload = () => {
			if (window.kakao && window.kakao.maps && window.kakao.maps.load) {
				window.kakao.maps.load(resolve);
			} else {
				// autoload=true 로 이미 로드됐을 수도 있으니 한번 더 점검
				if (window.kakao && window.kakao.maps) resolve();
				else reject(new Error('Kakao object missing'));
			}
		};
		s.onerror = () => reject(new Error('Kakao SDK load failed'));
		document.head.appendChild(s);
	});

	return kakaoReadyPromise;
}
// 전역 열림 플래그
window.__MAP_MODAL_OPEN = window.__MAP_MODAL_OPEN ?? false;
export async function openMapModal(targetLocationInput) {
	try {
		await loadKakaoSdkOnce(); // ✅ SDK 로드 보장
	} catch (e) {
		console.error('Kakao SDK load failed:', e);
		alert('지도를 불러오지 못했습니다. 관리자에게 문의해주세요. (SDK)');
		return;
	}
	// 1. 모달 HTML 동적 생성
	let mapModal = document.getElementById('kakaoMapModal');
	if (!mapModal) {
		mapModal = document.createElement('div');
		mapModal.id = 'kakaoMapModal';
		mapModal.className = 'kakao-map-modal';
		mapModal.style.zIndex = '11000';
		mapModal.innerHTML = `
	      <div class="map-modal-content">
	        <div id="map" style="width:100%;height:400px;"></div>
	        <div class="map-modal-footer">
	          <input type="text" id="mapSearchKeyword" placeholder="주소 또는 장소 검색">
	          <button id="mapSearchBtn" class="btn secondary small">검색</button>
	          <p id="mapSelectedAddress" style="font-size:0.9em;margin:10px 0;"></p>
	          <button id="mapConfirmBtn" class="btn primary">이 위치로 설정</button>
	          <button id="mapCloseBtn" class="btn secondary">닫기</button>
	        </div>
	      </div>`;
		document.body.appendChild(mapModal);

		// 지도 모달 내부 클릭은 버블링 차단
		mapModal.addEventListener('click', (e) => e.stopPropagation());
		const content = mapModal.querySelector('.map-modal-content');
		if (content) content.addEventListener('click', (e) => e.stopPropagation());

	}

	// CSS가 flex 기준이므로 flex로 열기
	mapModal.style.display = 'flex';
	window.__MAP_MODAL_OPEN = true;

	// 2. DOM 참조
	const mapContainer = mapModal.querySelector('#map');
	const searchKeyword = mapModal.querySelector('#mapSearchKeyword');
	const searchBtn = mapModal.querySelector('#mapSearchBtn');
	const selectedAddrEl = mapModal.querySelector('#mapSelectedAddress');
	const confirmBtn = mapModal.querySelector('#mapConfirmBtn');
	const closeBtn = mapModal.querySelector('#mapCloseBtn');

	let selectedAddress = ''; // 선택된 주소를 저장할 변수

	// 3. 카카오맵 생성
	const map = new kakao.maps.Map(mapContainer, {
		center: new kakao.maps.LatLng(37.566826, 126.9786567),
		level: 3
	});
	// 모달 표시 직후 강제 리레이아웃 (+ 약간의 지연)
	setTimeout(() => map.relayout(), 0);

	const geocoder = new kakao.maps.services.Geocoder();
	const ps = new kakao.maps.services.Places();
	const marker = new kakao.maps.Marker({ position: map.getCenter() });
	marker.setMap(map);

	// 4. 지도 클릭 이벤트 (주소 변환)
	kakao.maps.event.addListener(map, 'click', function(mouseEvent) {
		const latlng = mouseEvent.latLng;
		marker.setPosition(latlng);
		searchDetailAddrFromCoords(latlng, function(result, status) {
			if (status === kakao.maps.services.Status.OK) {
				selectedAddress = result[0].road_address
					? result[0].road_address.address_name
					: result[0].address.address_name;
				selectedAddrEl.textContent = '선택 위치: ' + selectedAddress;
			}
		});
	});

	// 5. 검색 기능
	function searchPlaces() {
		const keyword = searchKeyword.value.trim();
		if (!keyword) {
			alert('키워드를 입력해주세요.');
			return false;
		}
		ps.keywordSearch(keyword, placesSearchCB);
	}

	searchBtn.addEventListener('click', searchPlaces);
	searchKeyword.addEventListener('keydown', (e) => {
		if (e.key === 'Enter') searchPlaces();
	});

	function placesSearchCB(data, status, pagination) {
		if (status === kakao.maps.services.Status.OK) {
			const bounds = new kakao.maps.LatLngBounds();
			// 검색된 장소 중 첫 번째 장소로 지도 이동
			const place = data[0];
			const placePosition = new kakao.maps.LatLng(place.y, place.x);

			bounds.extend(placePosition);
			map.setBounds(bounds);
			marker.setPosition(placePosition);

			selectedAddress = place.road_address_name || place.address_name;
			selectedAddrEl.textContent = '선택 위치: ' + selectedAddress;

		} else if (status === kakao.maps.services.Status.ZERO_RESULT) {
			alert('검색 결과가 존재하지 않습니다.');
		} else {
			alert('검색 중 오류가 발생했습니다.');
		}
	}

	function searchDetailAddrFromCoords(coords, callback) {
		geocoder.coord2Address(coords.getLng(), coords.getLat(), callback);
	}

	// 6. 확인 및 닫기 버튼
	confirmBtn.onclick = () => {
		if (selectedAddress) {
			// 요청사항: 기존 입력값에 덮어쓰지 않고, 사용자가 수정 가능하도록 함
			targetLocationInput.value = selectedAddress;
		}
		mapModal.style.display = 'none';
		window.__MAP_MODAL_OPEN = false;
	};

	closeBtn.onclick = () => {
		mapModal.style.display = 'none';
		window.__MAP_MODAL_OPEN = false;
	};
}