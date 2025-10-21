package com.smhrd.web.dto;

import com.smhrd.web.entity.Schedule;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleRequestDto {
    private String title;
    private String description;
    private String colorTag;
    private Boolean isAllDay = false;
    private LocalDateTime startTime; // 프론트에서 넘어오는 yyyy-MM-dd'T'HH:mm:ss 형식 문자열을 LocalDateTime으로 바로 받음
    private LocalDateTime endTime;
    private LocalDateTime alarmTime;
    
    // Quick Add Modal의 고급 옵션 필드
    private String emoji;
    private String alertType;
    private Integer customAlertValue;
    private String customAlertUnit; // 프론트에서 미사용이지만 엔티티에 있어 포함
    private String location;
    private Double mapLat;
    private Double mapLng;
    private String highlightType;
    private String category;
    private String attachmentPath;
    private String attachmentList = "[]";
    
    // ✅ DTO를 엔티티로 변환
    public Schedule toEntity() {
        return Schedule.builder()
                .title(this.title)
                .description(this.description)
                .colorTag(this.colorTag)
                .isAllDay(this.isAllDay)
                .startTime(this.startTime)
                .endTime(this.endTime)
                .alarmTime(this.alarmTime)
                .emoji(this.emoji)
                .alertType(this.alertType)
                .customAlertValue(this.customAlertValue)
                .customAlertUnit(this.customAlertUnit)
                .location(this.location)
                .mapLat(this.mapLat)
                .mapLng(this.mapLng)
                .highlightType(this.highlightType)
                .category(this.category)
                .attachmentPath(this.attachmentPath)
                .attachmentList(this.attachmentList)
                .build();
    }
}