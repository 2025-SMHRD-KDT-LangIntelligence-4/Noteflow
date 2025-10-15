package com.smhrd.web.repository;

import com.smhrd.web.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // user_id 기반 → user_idx(Long) 기반으로 변경
    List<Chat> findByUser_UserIdxOrderByCreatedAtDesc(Long userIdx);
}
