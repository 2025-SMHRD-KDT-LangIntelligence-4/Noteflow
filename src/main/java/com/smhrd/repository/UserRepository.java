package com.smhrd.repository;

import com.smhrd.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository
 * ----------------------
 * - Spring Data JPA의 JpaRepository를 상속받아 기본적인 CRUD 메서드 제공
 * - 별도의 구현 클래스 없이 인터페이스 정의만으로 즉시 사용 가능
 * - Optional<T> 반환 → NullPointerException 방지
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	
    // 이메일 중복 체크 (회원가입 시 사용)
    boolean existsByEmail(String email);

    // 이메일로 사용자 조회 (로그인, 마이페이지에서 사용)
    Optional<User> findByEmail(String email);

    // [추가] 사용자 이름(username) 중복 체크
    boolean existsByUsername(String username);  // [추가]

    // [추가] 사용자 이름(username)으로 조회
    Optional<User> findByUsername(String username);  // [추가]
}
