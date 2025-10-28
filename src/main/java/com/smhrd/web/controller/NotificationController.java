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
 * 웹 알림 관련 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final WebNotificationService webNotificationService;

    /**
     * 현재 사용자의 알림 목록 조회
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "로그인이 필요합니다.",
                "notifications", new HashMap<>()
            ));
        }

        Long userId = userDetails.getUserIdx();
        Map<String, Object> notifications = webNotificationService.getUserNotifications(userId);
        int unreadCount = webNotificationService.getUnreadCount(userId);

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

        Long userId = userDetails.getUserIdx();
        int unreadCount = webNotificationService.getUnreadCount(userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "unreadCount", unreadCount
        ));
    }

    /**
     * 알림 읽음 처리
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable String notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다."
            ));
        }

        Long userId = userDetails.getUserIdx();
        webNotificationService.markAsRead(userId, notificationId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "알림을 읽음 처리했습니다."
        ));
    }

    /**
     * 테스트용 알림 생성
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> createTestNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "로그인이 필요합니다."
            ));
        }

        Long userId = userDetails.getUserIdx();
        webNotificationService.sendTestNotification(userId);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "테스트 알림을 생성했습니다."
        ));
    }
}