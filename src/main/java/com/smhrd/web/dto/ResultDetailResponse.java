package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResultDetailResponse {
    // 시험 결과 요약
    private Long resultIdx;
    private Long testIdx;
    private String testTitle;
    private String testDesc;
    private Integer totalScore;
    private Integer userScore;
    private Integer correctCount;
    private Integer wrongCount;
    private Double passRate;
    private Boolean passed;
    private Integer testDuration;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    // 문제별 상세 답안
    private List<QuestionWithAnswerResponse> questions;
}
