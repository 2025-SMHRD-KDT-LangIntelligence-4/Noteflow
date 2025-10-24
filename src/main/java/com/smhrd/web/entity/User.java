package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    @Column(name = "user_idx")
    private Long userIdx;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "user_pw", nullable = false)
    private String userPw;

    @Column(name = "user_role", nullable = false)
    private String userRole;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "last_login")
    private LocalDateTime lastLogin; // 최근 로그인 시각

    @Column(name = "mailing_agreed", nullable = false)
    private Boolean mailingAgreed;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "account_enabled", nullable = false)
    private Boolean accountEnabled = false;
    
    @Column(name = "nickname")
    private String nickname;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "bio")
    private String bio; // 자기소개

    @Column(name = "interest_area")
    private String interestArea;

    @Column(name = "learning_area")
    private String learningArea;
    
    
    @Column(name = "is_suspended", nullable = false)
    private Boolean isSuspended = false; // 기본값 false

    @Column(name = "suspend_reason")
    private String suspendReason;

    @Column(name = "suspend_start_date")
    private LocalDateTime suspendStartDate;

    @Column(name = "suspend_end_date")
    private LocalDateTime suspendEndDate;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0; // 기본값 0

    @Column(name = "attachment_count", nullable = false)
    private Integer attachmentCount = 0; // 기본값 0

    @Column(name = "login_count", nullable = false)
    private Integer loginCount = 0; // 기본값 0

    @Column(name = "note_count", nullable = false)
    private Integer noteCount = 0; // 기본값 0

    @Column(name = "test_count", nullable = false)
    private Integer testCount = 0; // 기본값 0

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // 자동 생성 시각


    // 비밀번호 확인 (폼 검증용, DB 비저장)
    @Transient
    private String userPwConfirm;

    // 소셜 로그인 연동용 (DB 비저장)
    @Transient
    private String socialLogin;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();

	
}
