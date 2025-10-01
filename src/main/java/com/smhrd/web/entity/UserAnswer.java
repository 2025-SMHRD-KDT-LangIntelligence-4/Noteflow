package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long answerIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_item_idx", nullable = false)
    private TestItem testItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userAnswer;
}
