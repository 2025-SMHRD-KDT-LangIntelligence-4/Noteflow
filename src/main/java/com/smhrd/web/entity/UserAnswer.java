package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_idx")
    private Long answerIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_idx", nullable = false)
    private TestResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_source_idx", nullable = false)
    private TestSource testSource;

    @Column(name = "user_answer", length = 500)
    private String userAnswer;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "response_time")
    private Integer responseTime;  // 초 단위

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
