package com.smhrd.web.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ExamSubmitRequest {
    private Long testIdx;
    private Map<Long, String> answers;  // testSourceIdx -> userAnswer
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
