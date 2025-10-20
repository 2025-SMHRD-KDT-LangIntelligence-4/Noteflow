package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class StatisticsResponse {
    // 전체 통계
    private Long totalTests;  // 총 응시 횟수
    private Long passedTests;  // 합격 횟수
    private Long failedTests;  // 불합격 횟수
    private Double averageScore;  // 평균 점수
    private Double correctRate;  // 정답률 (%)

    // 카테고리별 취약점
    private Map<String, Double> weakCategories;  // 카테고리 -> 오답률(%)

    // 난이도별 통계
    private Map<String, DifficultyStats> difficultyStats;
}
