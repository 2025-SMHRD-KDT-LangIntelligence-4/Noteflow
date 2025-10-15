// src/main/java/com/smhrd/web/entity/TestSummary.java

package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_summaries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long testId;

    @Column(nullable = false)
    private String testType; // "TEXT" 또는 "FILE"

    @Column(name = "prompt_title")
    private String promptTitle;

    @Column(name = "original_content", columnDefinition = "LONGTEXT")
    private String originalContent;

    @Column(columnDefinition = "TEXT")
    private String originalPrompt;     // 추가: 원본 프롬프트

    @Column(name = "ai_summary", columnDefinition = "LONGTEXT")
    private String aiSummary;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "status")
    private String status; // "SUCCESS", "FAILED"

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;



    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
