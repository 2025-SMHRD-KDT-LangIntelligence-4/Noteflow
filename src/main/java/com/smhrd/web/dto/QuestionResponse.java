package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuestionResponse {
    private Long testSourceIdx;
    private Integer sequence;
    private String question;
    private String questionType;  // MULTIPLE_CHOICE, FILL_BLANK, CONCEPT, SUBJECTIVE
    private String options;  // JSON 형태의 선택지
    private Integer score;
    private String difficulty;  // "1", "2", "3", "4", "5"
    private String categoryLarge;
    private String categoryMedium;
    private String categorySmall;

    // 정답은 포함하지 않음 (시험 풀이용)
    // 채점 후 결과 조회 시에만 정답 노출
}
