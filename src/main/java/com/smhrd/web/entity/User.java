package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userIdx;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String userPw;

    @Column(nullable = false)
    private String userRole;

    @Column(nullable = false, unique = true)
    private String email;

    private LocalDateTime lastLogin;

    @Column(nullable = false)
    private Boolean mailingAgreed;

    private String interestArea;
    private String learningArea;

    @Column(nullable = false)
    private Integer attachmentCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
