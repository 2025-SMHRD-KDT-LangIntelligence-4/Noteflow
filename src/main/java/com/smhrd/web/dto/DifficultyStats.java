package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DifficultyStats {
    private String difficulty;  // "1", "2", "3", "4", "5"
    private Long totalQuestions;  // 총 문제 수
    private Long answeredQuestions;  // 풀이한 문제 수
    private Long correctAnswers;  // 정답 수
    private Double correctRate;  // 정답률 (%)
}
