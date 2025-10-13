package com.smhrd.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty; // [추가]
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    @JsonProperty("scheduleId") // [추가]
    private Long scheduleId;

    /*
     * User 엔티티와 ManyToOne 매핑
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private User user;

    @Column(nullable = false)
    @JsonProperty("title") // [추가]
    private String title;

    // FullCalendar와 호환되는 ISO 형식으로 직렬화
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "start_time", nullable = false)
    @JsonProperty("startTime") // [추가]
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "end_time")
    @JsonProperty("endTime") // [추가]
    private LocalDateTime endTime;

    @Column(name = "color_tag")
    @JsonProperty("colorTag") // [추가]
    private String colorTag; // 예: #3788d8

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("createdAt") // [추가]
    private LocalDateTime createdAt;

    // 생성 전 기본 값 세팅
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
