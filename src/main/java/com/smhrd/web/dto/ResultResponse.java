package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ResultResponse {
    private Long resultIdx;
    private Long testIdx;
    private String testTitle;
    private Integer totalScore;
    private Integer userScore;
    private Integer correctCount;
    private Integer wrongCount;
    private Double passRate;  // 백분율 (0~100)
    private Boolean passed;
    private Integer testDuration;  // 분 단위
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
