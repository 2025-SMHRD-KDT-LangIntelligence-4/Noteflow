package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionWithAnswerResponse {
    private Long testSourceIdx;
    private Integer sequence;
    private String question;
    private String answer;  // 정답
    private String explanation;  // 해설
    private String questionType;
    private String options;
    private Integer score;
    private String difficulty;
    private String categoryLarge;
    private String categoryMedium;
    private String categorySmall;

    // 사용자 답안 정보
    private String userAnswer;
    private Boolean isCorrect;
    private Integer responseTime;
}
