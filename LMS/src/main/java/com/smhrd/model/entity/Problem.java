package com.smhrd.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String question;  // 문제

    @Column(columnDefinition = "TEXT")
    private String answer;    // 정답

    @Column
    private String category;  // 분류 (예: 알고리즘, DB 등)
}
