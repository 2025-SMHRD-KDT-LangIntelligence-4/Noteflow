package com.smhrd.web.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 노트 저장/수정 이벤트
 */
@Getter
public class NoteSavedEvent extends ApplicationEvent {
    private final Long noteIdx;
    private final Long userIdx;

    public NoteSavedEvent(Object source, Long noteIdx, Long userIdx) {
        super(source);
        this.noteIdx = noteIdx;
        this.userIdx = userIdx;
    }
}
