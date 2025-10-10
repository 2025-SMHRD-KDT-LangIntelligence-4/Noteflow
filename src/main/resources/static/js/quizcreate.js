document.addEventListener('DOMContentLoaded', () => {
    // 필요한 DOM 요소를 모두 가져옵니다.
    const treeContainer = document.querySelector('.tree-container');
    const selectedTagSpan = document.getElementById('selectedTag');
    const completeButton = document.getElementById('selectCompleteBtn');
    
    // HTML에 미리 'selected' 클래스가 적용된 항목이 있는지 확인하고 버튼 상태 설정
    const initialSelected = treeContainer.querySelector('.tree-item.selected');
    if (initialSelected) {
        completeButton.disabled = false;
        // 초기 태그값 설정 (HTML에서 data-tag 속성을 읽어옴)
        selectedTagSpan.textContent = initialSelected.getAttribute('data-tag') || "선택된 태그가 없습니다.";
    } else {
        completeButton.disabled = true;
    }

    /**
     * 노션 문서 항목 클릭 시 실행되는 함수
     * @param {Event} event - 클릭 이벤트 객체
     */
    const handleItemClick = (event) => {
        // 클릭된 요소 또는 부모 요소 중 .tree-item을 찾습니다.
        let clickedItem = event.target.closest('.tree-item');

        // guide-text나 다른 요소를 클릭했다면 무시합니다.
        if (!clickedItem || clickedItem.classList.contains('guide-text')) {
            return;
        }

        // 1. 기존 선택 해제
        const currentSelected = treeContainer.querySelector('.tree-item.selected');
        // 현재 선택된 항목이 있고, 그것이 방금 클릭된 항목과 다르다면
        if (currentSelected && currentSelected !== clickedItem) {
            currentSelected.classList.remove('selected');
        }
        
        // 2. 새로운 항목 선택 (클릭된 항목에 'selected' 클래스 추가)
        clickedItem.classList.add('selected');
        
        // 3. 하단 정보 업데이트 및 버튼 상태 변경
        const documentTag = clickedItem.getAttribute('data-tag');

        if (documentTag) {
            // 태그 업데이트 (HTML의 data-tag 속성을 사용)
            selectedTagSpan.textContent = documentTag;
            
            // 버튼 활성화
            completeButton.disabled = false;
        } else {
            // 태그가 없는 경우
            selectedTagSpan.textContent = "템플릿 정보 없음";
            completeButton.disabled = false;
        }
    };

    // 트리 컨테이너에 클릭 이벤트 리스너를 추가합니다.
    treeContainer.addEventListener('click', handleItemClick);
    
    // (옵션) 선택 완료 버튼 클릭 이벤트 (다음 페이지 이동 로직)
    completeButton.addEventListener('click', () => {
        const selectedItem = treeContainer.querySelector('.tree-item.selected');
        if (selectedItem) {
            const documentTitle = selectedItem.textContent.trim();
            const documentTag = selectedItem.getAttribute('data-tag');
            
            alert(`"${documentTitle}" (태그: ${documentTag}) 문서를 선택했습니다. 다음 퀴즈 생성 페이지로 이동합니다!`);
            
            // 여기에 window.location.href = '/next-quiz-generation-page' 와 같은 실제 이동 코드를 넣으면 됩니다.
        }
    });
});