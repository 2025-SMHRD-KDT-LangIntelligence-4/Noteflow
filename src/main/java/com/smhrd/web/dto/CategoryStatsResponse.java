package com.smhrd.web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class CategoryStatsResponse {
    private String categoryLarge;
    private Long totalQuestions;
    private Map<String, Long> difficultyDistribution;  // "1" -> count, "2" -> count, ...
    private Map<String, Long> questionTypeDistribution;  // MULTIPLE_CHOICE -> count, ...
}
