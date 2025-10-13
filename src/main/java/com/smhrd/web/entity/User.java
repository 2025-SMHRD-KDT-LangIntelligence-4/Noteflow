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
    private LocalDateTime lastLogin; // [수정] @Transient 제거 → DB 컬럼 매핑

    @Column(name = "mailing_agreed", nullable = false)
    private Boolean mailingAgreed;

    @Column(name = "nickname")
    private String nickname; // [수정] @Transient 제거 및 이름 소문자 변경

    @Column(name = "profile_image")
    private String profileImage; // [수정] DB 컬럼명에 맞게 변경 (profile_image_url → profile_image)

    @Column(name = "bio")
    private String bio; // [추가] 자기소개

    @Column(name = "interest_area")
    private String interestArea;

    @Column(name = "learning_area")
    private String learningArea;

    @Column(name = "is_suspended")
    private Boolean isSuspended; // [추가] 계정 정지 여부

    @Column(name = "suspend_reason")
    private String suspendReason; // [추가] 정지 사유

    @Column(name = "suspend_start_date")
    private LocalDateTime suspendStartDate; // [추가]

    @Column(name = "suspend_end_date")
    private LocalDateTime suspendEndDate; // [추가]

    @Column(name = "warning_count")
    private Integer warningCount; // [추가]

    @Column(name = "attachment_count", nullable = false)
    private Integer attachmentCount;

    @Column(name = "login_count")
    private Integer loginCount; // [추가]

    @Column(name = "note_count")
    private Integer noteCount; // [추가]

    @Column(name = "test_count")
    private Integer testCount; // [추가]

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // [수정] 자동 생성 시간 반영

    // 비밀번호 확인 등 폼 전용 필드 (DB 비저장)
    @Transient
    private String userPwConfirm; // [유지: DB 저장 X, 폼 검증용]

    @Transient
    private String socialLogin; // [유지: 소셜 로그인 연동 시 사용]
    

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Note> notes = new ArrayList<>();
    
}
