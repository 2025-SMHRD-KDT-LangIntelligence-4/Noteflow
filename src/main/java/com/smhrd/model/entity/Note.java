package com.smhrd.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "note")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;  // 작성자

    @Column(nullable = false)
    private String title;  

    @Column(columnDefinition = "TEXT")
    private String content;  // 회고록/정리 내용

    @Column
    private LocalDateTime createdAt = LocalDateTime.now();
}
