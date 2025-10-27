package com.smhrd.web.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 시험 결과 저장 이벤트
 */
@Getter
public class ExamResultSavedEvent extends ApplicationEvent {
    private final Long resultIdx;
    private final Long userIdx;
    private final Long testIdx;

    public ExamResultSavedEvent(Object source, Long resultIdx, Long userIdx, Long testIdx) {
        super(source);
        this.resultIdx = resultIdx;
        this.userIdx = userIdx;
        this.testIdx = testIdx;
    }
}
