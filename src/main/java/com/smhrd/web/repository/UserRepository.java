package com.smhrd.web.repository;

import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdx(String userId);
    Optional<User> findByEmail(String email);

    Optional<User> findByUserIdAndIsSuspendedFalse(String userId);

    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    void updateLastLogin(@Param("userId") String userId);
}
