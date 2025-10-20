package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_source_idx")
    private Long testSourceIdx;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 10)
    private String difficulty;

    @Column(name = "category_large", length = 100)
    private String categoryLarge;

    @Column(name = "category_medium", length = 100)
    private String categoryMedium;

    @Column(name = "category_small", length = 100)
    private String categorySmall;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String options;  // JSON 형태의 선택지

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
