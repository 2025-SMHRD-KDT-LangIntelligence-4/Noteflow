package com.smhrd.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEventDto {
    private Long schedule_id;
    private String title;
    private String start_time; 
    private String end_time;
    private String color_tag;
    private String description; // [추가]
    private Boolean is_all_day; // [추가]
    private String emoji;       // [추가]
}
