package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lectures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lecture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lecIdx;

    @Column(nullable = false)
    private String lecTitle;

    @Column(nullable = false)
    private String lecUrl;

    @Column(nullable = false)
    private String categoryLarge;

    @Column(nullable = false)
    private String categoryMedium;

    @Column(nullable = false)
    private String categorySmall;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "video_file_id")
    private String videoFileId;

}
