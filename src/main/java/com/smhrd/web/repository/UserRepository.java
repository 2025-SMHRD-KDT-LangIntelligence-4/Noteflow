package com.smhrd.web.repository;

import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 기본 조회
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email); // [추가]
    // [추가] 로그인 시 정지되지 않은 유저만 조회
    Optional<User> findByUserIdAndIsSuspendedFalse(String userId); // [추가]

    // [추가] 닉네임 및 이메일 중복 확인
    boolean existsByNickname(String nickname); // [추가]
    boolean existsByEmail(String email);       // [추가]

    // [추가] 로그인 시각 업데이트
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    void updateLastLogin(@Param("userId") String userId); // [추가]
}
