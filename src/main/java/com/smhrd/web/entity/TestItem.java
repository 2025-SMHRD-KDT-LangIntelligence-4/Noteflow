package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_ti", columnNames = {"test_idx", "test_source_idx"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_idx")
    private Long itemIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_idx", nullable = false)
    private Test test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_source_idx", nullable = false)
    private TestSource testSource;

    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 1;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
