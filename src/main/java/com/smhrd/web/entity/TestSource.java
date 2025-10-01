package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "test_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sourceIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_idx", nullable = false)
    private Test test;

    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceContent;
}
