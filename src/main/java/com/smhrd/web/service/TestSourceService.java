package com.smhrd.web.service;

import com.smhrd.web.entity.QuestionType;
import com.smhrd.web.entity.TestSource;
import com.smhrd.web.repository.TestSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestSourceService {

    private final TestSourceRepository testSourceRepository;

    /**
     * 문제 검색
     */
    @Transactional(readOnly = true)
    public List<TestSource> searchQuestions(String keyword) {
        return testSourceRepository.searchByKeyword(keyword);
    }

    /**
     * 카테고리별 문제 조회
     * @param difficulty 난이도 (1~5)
     */
    @Transactional(readOnly = true)
    public Page<TestSource> getQuestionsByCategory(String categoryLarge, String difficulty,
                                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 난이도 검증
        if (difficulty != null && !difficulty.isEmpty() && !difficulty.matches("[1-5]")) {
            throw new IllegalArgumentException("난이도는 1~5 사이의 숫자여야 합니다.");
        }

        if (difficulty != null && !difficulty.isEmpty()) {
            return testSourceRepository.findByCategoryLargeAndDifficulty(categoryLarge, difficulty, pageable);
        }

        return testSourceRepository.findByCategoryLarge(categoryLarge, pageable);
    }

    /**
     * 문제 상세 조회
     */
    @Transactional(readOnly = true)
    public TestSource getQuestion(Long testSourceIdx) {
        return testSourceRepository.findById(testSourceIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문제입니다."));
    }

    /**
     * 랜덤 문제 조회
     * @param difficulty 난이도 (1~5)
     */
    @Transactional(readOnly = true)
    public List<TestSource> getRandomQuestions(String categoryLarge, String difficulty, int count) {
        if (difficulty != null && !difficulty.isEmpty() && !difficulty.matches("[1-5]")) {
            throw new IllegalArgumentException("난이도는 1~5 사이의 숫자여야 합니다.");
        }

        if (difficulty != null && !difficulty.isEmpty()) {
            return testSourceRepository.findRandomByCategoryLargeAndDifficulty(categoryLarge, difficulty, count);
        }
        return testSourceRepository.findRandomByCategoryLarge(categoryLarge, count);
    }

    /**
     * 문제 통계 (난이도별)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getQuestionStatistics(String categoryLarge) {
        long total = testSourceRepository.countByCategoryLarge(categoryLarge);

        Map<String, Long> difficultyCount = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            String diff = String.valueOf(i);
            long count = testSourceRepository.countByCategoryLargeAndDifficulty(categoryLarge, diff);
            difficultyCount.put("level" + i, count);
        }

        return Map.of(
                "category", categoryLarge,
                "total", total,
                "byDifficulty", difficultyCount
        );
    }
}
