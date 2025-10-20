package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class WrongAnswerResponse {
    private Long answerIdx;
    private Long testSourceIdx;
    private String question;
    private String correctAnswer;
    private String userAnswer;
    private String explanation;
    private String questionType;
    private String options;
    private String difficulty;
    private String categoryLarge;
    private String categoryMedium;
    private String categorySmall;
    private LocalDateTime createdAt;  // 오답 발생 시각

    // 시험 정보
    private Long testIdx;
    private String testTitle;
}
