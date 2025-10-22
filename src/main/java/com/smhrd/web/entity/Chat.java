package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "chats")
@Data
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatIdx;

    @Column(nullable = false)
    private Long userIdx;

    @Column(length = 100)
    private String sessionId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;

    @Column
    private Integer responseTimeMs;

    @Column
    private Byte rating;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
