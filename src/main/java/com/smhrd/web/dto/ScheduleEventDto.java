package com.smhrd.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor // ✅ 이게 있으면 자동으로 생성되지만, 혹시 모를 충돌을 위해 아래 생성자 추가 권장
public class ScheduleEventDto {
    private Long schedule_id;
    private String title;
    private String start_time; 
    private String end_time;
    private String color_tag;
    private String description;
    private Boolean is_all_day;
    private String emoji;
}