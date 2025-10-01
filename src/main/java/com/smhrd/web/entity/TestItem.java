package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_idx", nullable = false)
    private Test test;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    private Integer score;
}
