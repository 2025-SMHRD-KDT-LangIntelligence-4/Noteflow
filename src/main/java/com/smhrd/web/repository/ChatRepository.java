package com.smhrd.web.repository;

import com.smhrd.web.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    List<Chat> findByUserUserIdOrderByCreatedAtDesc(String userId);
}
