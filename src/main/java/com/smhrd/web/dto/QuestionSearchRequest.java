package com.smhrd.web.dto;

import lombok.Data;

@Data
public class QuestionSearchRequest {
    private String keyword;
    private String categoryLarge;
    private String categoryMedium;
    private String categorySmall;
    private String difficulty;  // "1", "2", "3", "4", "5"
    private String questionType;
    private Integer page;
    private Integer size;
}
