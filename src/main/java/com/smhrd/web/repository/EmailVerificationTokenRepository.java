package com.smhrd.web.repository;

import com.smhrd.web.entity.EmailVerificationToken;
import com.smhrd.web.entity.EmailVerificationToken.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    
    Optional<EmailVerificationToken> findByTokenAndTokenTypeAndUsed(String token, TokenType tokenType, Boolean used);
    
    @Modifying
    @Transactional
    void deleteByEmailAndTokenTypeAndUsed(String email, TokenType tokenType, Boolean used);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiryDate < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
