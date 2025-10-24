package com.smhrd.web.repository;

import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByNickname(String nickname);
    Optional<User> findByUserIdAndIsSuspendedFalse(String userId);

    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    void updateLastLogin(@Param("userId") String userId);
    
    Optional<User> findByUserIdx(Long userIdx);
    

    @Query("SELECT u FROM User u WHERE u.lastLogin < :date AND u.mailingAgreed = true AND u.emailVerified = true")
    List<User> findUsersNotLoginSince(LocalDateTime date);

    @Query("SELECT u FROM User u WHERE u.lastLogin > :date AND u.mailingAgreed = true AND u.emailVerified = true")
    List<User> findActiveUsersWithMailingConsent(LocalDateTime date);

}
