package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamResponse {
    private Long testIdx;
    private String testTitle;
    private String testDesc;
    private Integer questionCount;
    private Integer totalScore;
    private LocalDateTime createdAt;
    private List<QuestionResponse> questions;
}
