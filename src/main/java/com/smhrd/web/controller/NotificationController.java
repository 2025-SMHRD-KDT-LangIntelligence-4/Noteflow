package com.smhrd.web.controller;

import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.WebNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 알림 관련 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    
    private final WebNotificationService webNotificationService;
    
    /**
     * 내 알림 목록 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.ok(Map.of(
                "success", false, 
                "message", "인증이 필요합니다.",
                "notifications", new HashMap<>(),
                "unreadCount", 0
            ));
        }
        
        Long userIdx = userDetails.getUserIdx();
        Map<String, Object> notifications = webNotificationService.getUserNotifications(userIdx);
        int unreadCount = webNotificationService.getUnreadCount(userIdx);
        
        log.info("📋 알림 목록 조회 - 사용자: {}, 개수: {}, 읽지않음: {}", userIdx, notifications.size(), unreadCount);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "notifications", notifications,
            "unreadCount", unreadCount
        ));
    }
    
    /**
     * 읽지 않은 알림 개수만 조회
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.ok(Map.of("unreadCount", 0));
        }
        
        Long userIdx = userDetails.getUserIdx();
        int unreadCount = webNotificationService.getUnreadCount(userIdx);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "unreadCount", unreadCount
        ));
    }
    
    /**
     * 특정 알림 읽음 처리
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable String notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "인증이 필요합니다."
            ));
        }
        
        Long userIdx = userDetails.getUserIdx();
        boolean success = webNotificationService.markAsRead(userIdx, notificationId);
        int newUnreadCount = webNotificationService.getUnreadCount(userIdx);
        
        log.info("✅ 알림 읽음 처리 - 사용자: {}, 알림ID: {}, 성공: {}", userIdx, notificationId, success);
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success ? "읽음 처리되었습니다." : "해당 알림을 찾을 수 없습니다.",
            "unreadCount", newUnreadCount
        ));
    }
    
    /**
     * 모든 알림 읽음 처리
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "인증이 필요합니다."
            ));
        }
        
        Long userIdx = userDetails.getUserIdx();
        webNotificationService.markAllAsRead(userIdx);
        
        log.info("✅ 모든 알림 읽음 처리 완료 - 사용자: {}", userIdx);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "모든 알림이 읽음 처리되었습니다.",
            "unreadCount", 0
        ));
    }
    
    /**
     * 특정 알림 삭제
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, Object>> deleteNotification(
            @PathVariable String notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "인증이 필요합니다."
            ));
        }
        
        Long userIdx = userDetails.getUserIdx();
        boolean success = webNotificationService.deleteNotification(userIdx, notificationId);
        int newUnreadCount = webNotificationService.getUnreadCount(userIdx);
        
        log.info("🗑️ 알림 삭제 - 사용자: {}, 알림ID: {}, 성공: {}", userIdx, notificationId, success);
        
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success ? "알림이 삭제되었습니다." : "해당 알림을 찾을 수 없습니다.",
            "unreadCount", newUnreadCount
        ));
    }
    
    /**
     * 테스트 알림 생성 (개발/테스트용)
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> createTestNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "테스트 알림") String title,
            @RequestParam(defaultValue = "알림 시스템 테스트입니다!") String message) {
        
        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "message", "인증이 필요합니다."
            ));
        }
        
        Long userIdx = userDetails.getUserIdx();
        webNotificationService.sendTestNotification(userIdx, title, message);
        
        log.info("🧪 테스트 알림 생성 - 사용자: {}, 제목: {}", userIdx, title);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "테스트 알림이 생성되었습니다."
        ));
    }
}