package com.smhrd.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteSimpleDto {
    private Long noteIdx;
    private String title;
    private LocalDateTime createdAt;
}
