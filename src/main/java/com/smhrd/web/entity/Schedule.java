package com.smhrd.web.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(name = "schedule") // 실제 테이블명과 일치
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    @JsonProperty("scheduleId")
    private Long scheduleId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private User user;

    @Column(nullable = false)
    @JsonProperty("title")
    private String title;

    @Column(columnDefinition = "TEXT")
    @JsonProperty("description")
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "start_time", nullable = false)
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "end_time")
    @JsonProperty("endTime")
    private LocalDateTime endTime;

    @Column(name = "color_tag")
    @JsonProperty("colorTag")
    private String colorTag;

    @Column(name = "alarm_time")
    @JsonProperty("alarmTime")
    private LocalDateTime alarmTime;

    @Column(name = "attachment_path")
    @JsonProperty("attachmentPath")
    private String attachmentPath;

    @Column(name = "is_all_day")
    @JsonProperty("isAllDay")
    private Boolean isAllDay = false;

    @Column(name = "is_deleted")
    @JsonProperty("isDeleted")
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    @Column(name = "emoji")
    @JsonProperty("emoji")
    private String emoji;

    @Column(name = "alert_type")
    @JsonProperty("alertType")
    private String alertType;

    @Column(name = "custom_alert_value")
    @JsonProperty("customAlertValue")
    private Integer customAlertValue;

    @Column(name = "custom_alert_unit")
    @JsonProperty("customAlertUnit")
    private String customAlertUnit;

    @Column(name = "location")
    @JsonProperty("location")
    private String location;

    @Column(name = "map_lat")
    @JsonProperty("mapLat")
    private Double mapLat;

    @Column(name = "map_lng")
    @JsonProperty("mapLng")
    private Double mapLng;

    @Column(name = "highlight_type")
    @JsonProperty("highlightType")
    private String highlightType;

    @Column(name = "category")
    @JsonProperty("category")
    private String category;

    @Column(name = "attachment_list")
    @JsonProperty("attachmentList")
    private String attachmentList = "[]";
    
    @Column(name = "email_notification_enabled")
    private Boolean emailNotificationEnabled = false;
    
    @Column(name = "notification_minutes_before")
    private Integer notificationMinutesBefore = 30;
    
    @Column(name = "email_notification_sent")
    private Boolean emailNotificationSent = false;
    
    @Column(name = "quartz_job_id", length = 255)
    private String quartzJobId;
}
