package com.smhrd.web.repository;

import com.smhrd.web.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    // 세션별 대화 조회
    List<Chat> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    // 사용자별 최근 대화 조회
    List<Chat> findByUserIdxOrderByCreatedAtDesc(Long userIdx);

    // 사용자별 전체 대화
    List<Chat> findByUserIdx(Long userIdx);

    // 세션별 대화 개수
    int countBySessionId(String sessionId);

    // 세션 삭제
    void deleteBySessionId(String sessionId);
}
