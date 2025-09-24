package com.smhrd.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder   // [추가] 빌더 패턴 적용 → 가독성 높은 객체 생성 가능
@Table(name = "users")
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false)
    private String password;  // 암호화된 비밀번호 저장

    @Column(nullable = false, unique = true, length = 50)
    private String email;

    @Column(nullable = false)
    private String role; // 기본 권한
}