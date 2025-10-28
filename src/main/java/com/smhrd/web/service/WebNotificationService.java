package com.smhrd.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 웹 브라우저 알림 서비스 (기존 이메일 서비스와 완전히 독립)
 */
@Slf4j
@Service
public class WebNotificationService {

    // 간단한 인메모리 저장소 (실제로는 Redis나 DB 사용 권장)
    private final Map<Long, Map<String, Object>> userNotifications = new ConcurrentHashMap<>();

    /**
     * 일정 알림을 사용자에게 저장
     */
    public void sendScheduleNotification(Long userId, String title, String description, LocalDateTime scheduleTime) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", "schedule_" + System.currentTimeMillis());
            notification.put("type", "schedule");
            notification.put("title", "📅 일정 알림: " + title);
            notification.put("message", description != null ? description : "곧 시작 예정입니다!");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("scheduleTime", scheduleTime);
            notification.put("read", false);

            // 사용자별 알림 저장
            userNotifications.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                            .put("latest_" + System.currentTimeMillis(), notification);

            log.info("🔔 웹 알림 저장 완료: 사용자 {} - {}", userId, title);

            // 실시간 알림 (브라우저 Notification API용 데이터 준비)
            prepareRealTimeNotification(userId, notification);

        } catch (Exception e) {
            log.error("❌ 웹 알림 저장 실패: 사용자 {} - {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 사용자의 알림 목록 조회
     */
    public Map<String, Object> getUserNotifications(Long userId) {
        Map<String, Object> userNotifs = userNotifications.getOrDefault(userId, new HashMap<>());

        // 최근 10개만 반환
        return userNotifs.entrySet().stream()
                .sorted((e1, e2) -> {
                    Map<String, Object> n1 = (Map<String, Object>) e1.getValue();
                    Map<String, Object> n2 = (Map<String, Object>) e2.getValue();
                    LocalDateTime t1 = (LocalDateTime) n1.get("timestamp");
                    LocalDateTime t2 = (LocalDateTime) n2.get("timestamp");
                    return t2.compareTo(t1); // 최신순
                })
                .limit(10)
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    /**
     * 알림 읽음 처리
     */
    public void markAsRead(Long userId, String notificationId) {
        Map<String, Object> userNotifs = userNotifications.get(userId);
        if (userNotifs != null && userNotifs.containsKey(notificationId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) userNotifs.get(notificationId);
            notification.put("read", true);
            log.info("📖 알림 읽음 처리: 사용자 {} - {}", userId, notificationId);
        }
    }

    /**
     * 읽지 않은 알림 개수
     */
    public int getUnreadCount(Long userId) {
        Map<String, Object> userNotifs = userNotifications.get(userId);
        if (userNotifs == null) return 0;

        return (int) userNotifs.values().stream()
                .map(n -> (Map<String, Object>) n)
                .filter(n -> !Boolean.TRUE.equals(n.get("read")))
                .count();
    }

    /**
     * 실시간 알림 준비 (나중에 WebSocket으로 전송할 데이터)
     */
    private void prepareRealTimeNotification(Long userId, Map<String, Object> notification) {
        // 현재는 로그만 출력, 나중에 WebSocket으로 실시간 전송
        log.info("🔔 실시간 알림 준비: 사용자 {} - {}", userId, notification.get("title"));

        // TODO: WebSocket 구현 시 여기서 실시간 전송
        // webSocketService.sendToUser(userId, notification);
    }

    /**
     * 테스트용 알림 생성
     */
    public void sendTestNotification(Long userId) {
        sendScheduleNotification(
            userId, 
            "테스트 일정", 
            "웹 알림 테스트입니다!", 
            LocalDateTime.now().plusMinutes(5)
        );
    }
}