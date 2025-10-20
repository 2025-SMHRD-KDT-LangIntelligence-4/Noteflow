package com.smhrd.web.dto;

import lombok.Data;

@Data
public class ExamCreateRequest {
    private Long noteIdx;
    private String title;
    private String description;
    private String categoryLarge;
    private String categoryMedium;
    private String categorySmall;
    private String difficulty;  // "1"~"5" 또는 null
    private Integer minDifficulty;
    private Integer maxDifficulty;
    private Integer questionCount;
    private Integer scorePerQuestion;
    private String questionType;
    private Boolean adaptiveDifficulty;
}
