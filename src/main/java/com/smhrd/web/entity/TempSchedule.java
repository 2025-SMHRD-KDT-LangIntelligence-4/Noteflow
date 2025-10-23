package com.smhrd.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temp_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "temp_id")
    @JsonProperty("tempId")
    private Long tempId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "color_tag")
    private String colorTag;

    @Column(name = "emoji")
    private String emoji;

    @Column(name = "alarm_time")
    private LocalDateTime alarmTime;

    @Column(name = "alert_type")
    private String alertType;

    @Column(name = "custom_alert_value")
    private Integer customAlertValue;

    @Column(name = "custom_alert_unit")
    private String customAlertUnit;

    @Column(name = "location")
    private String location;

    @Column(name = "map_lat")
    private Double mapLat;

    @Column(name = "map_lng")
    private Double mapLng;

    @Column(name = "highlight_type")
    private String highlightType;

    @Column(name = "category")
    private String category;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "attachment_list")
    private String attachmentList;

    @Column(name = "is_all_day")
    private Boolean isAllDay = false;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (isAllDay == null) isAllDay = false;
        if (isDeleted == null) isDeleted = false;
    }
    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
