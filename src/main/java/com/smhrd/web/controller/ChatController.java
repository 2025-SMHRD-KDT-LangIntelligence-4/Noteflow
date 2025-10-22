package com.smhrd.web.controller;

import com.smhrd.web.entity.Chat;
import com.smhrd.web.service.ChatService;
import com.smhrd.web.security.CustomUserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * 채팅 메시지 전송 (RAG 기반)
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(
            @RequestBody ChatRequest request,
            Authentication auth) {

        try {
            Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
            ChatResponse response = chatService.processChat(
                    userIdx,
                    request.getMessage(),
                    request.getSessionId()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ChatResponse(
                            "죄송합니다. 오류가 발생했습니다.",
                            0,
                            request.getSessionId()
                    ));
        }
    }

    /**
     * 세션별 대화 히스토리 조회
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatHistoryDto>> getChatHistory(
            @RequestParam(required = false) String sessionId,
            Authentication auth) {

        try {
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            List<Chat> history = chatService.getSessionHistory(sessionId);
            List<ChatHistoryDto> response = history.stream()
                    .map(chat -> new ChatHistoryDto(
                            chat.getChatIdx(),
                            chat.getQuestion(),
                            chat.getAnswer(),
                            chat.getResponseTimeMs(),
                            chat.getCreatedAt().toString()
                    ))
                    .toList();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * 사용자별 최근 대화 조회
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ChatHistoryDto>> getRecentChats(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {

        try {
            Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
            List<Chat> recentChats = chatService.getUserRecentChats(userIdx, limit);

            List<ChatHistoryDto> response = recentChats.stream()
                    .map(chat -> new ChatHistoryDto(
                            chat.getChatIdx(),
                            chat.getQuestion(),
                            chat.getAnswer(),
                            chat.getResponseTimeMs(),
                            chat.getCreatedAt().toString()
                    ))
                    .toList();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * 특정 대화 평가 (별점/피드백)
     */
    @PostMapping("/rate/{chatIdx}")
    public ResponseEntity<Map<String, String>> rateChat(
            @PathVariable Long chatIdx,
            @RequestBody RatingRequest request,
            Authentication auth) {

        try {
            chatService.rateChat(chatIdx, request.getRating(), request.getFeedback());
            return ResponseEntity.ok(Map.of("status", "success"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * 세션 삭제 (대화 초기화)
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable String sessionId,
            Authentication auth) {

        try {
            chatService.deleteSession(sessionId);
            return ResponseEntity.ok(Map.of("status", "success"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error"));
        }
    }

    /**
     * 대화 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<ChatStatsDto> getChatStats(Authentication auth) {
        try {
            Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
            ChatStatsDto stats = chatService.getUserChatStats(userIdx);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ChatStatsDto(0, 0, 0.0));
        }
    }

    // ========== DTO 클래스 ==========

    @Getter
    @Setter
    public static class ChatRequest {
        private String message;
        private String sessionId;
    }

    @AllArgsConstructor
    @Getter
    public static class ChatResponse {
        private String reply;
        private int historyCount;
        private String sessionId;
    }

    @AllArgsConstructor
    @Getter
    public static class ChatHistoryDto {
        private Long chatIdx;
        private String question;
        private String answer;
        private Integer responseTimeMs;
        private String createdAt;
    }

    @Getter
    @Setter
    public static class RatingRequest {
        private Byte rating;
        private String feedback;
    }

    @AllArgsConstructor
    @Getter
    public static class ChatStatsDto {
        private int totalChats;
        private int avgResponseTimeMs;
        private double avgRating;
    }
}
