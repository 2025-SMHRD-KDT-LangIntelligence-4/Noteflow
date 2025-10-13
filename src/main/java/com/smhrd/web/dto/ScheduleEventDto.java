package com.smhrd.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEventDto {
    private Long scheduleId;
    private String title;
    private String startTime; // ISO string, e.g. 2025-10-10T00:00:00
    private String endTime;
    private String colorTag;
}
