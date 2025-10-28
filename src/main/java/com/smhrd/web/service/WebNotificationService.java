package com.smhrd.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WebNotificationService {
    
    // 메모리 기반 알림 저장 (Redis나 DB로 대체 가능)
    private final Map<Long, Map<String, Object>> userNotifications = new ConcurrentHashMap<>();
    
    @Autowired(required = false)  // WebSocket이 없을 수도 있으니
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * 일정 알림 전송
     */
    public void sendScheduleNotification(Long userIdx, String title, String description, LocalDateTime scheduleTime) {
        try {
            String notificationId = "schedule_" + System.currentTimeMillis();
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", notificationId);
            notification.put("type", "schedule");
            notification.put("title", "📅 일정 알림: " + title);
            notification.put("message", description != null ? description : "일정 시간입니다!");
            notification.put("timestamp", LocalDateTime.now());
            notification.put("scheduleTime", scheduleTime);
            notification.put("read", false);
            
            // 사용자별 알림 저장 (최신 알림을 latest 키에 저장)
            userNotifications.computeIfAbsent(userIdx, k -> new ConcurrentHashMap<>())
                            .put("latest_" + System.currentTimeMillis(), notification);
            
            log.info("📅 일정 알림 저장 완료 - 사용자: {}, 제목: {}", userIdx, title);
            
            // WebSocket으로 실시간 전송
            sendRealTimeNotification(userIdx, notification);
            
        } catch (Exception e) {
            log.error("일정 알림 저장 실패 - 사용자: {}, 오류: {}", userIdx, e.getMessage(), e);
        }
    }
    
    /**
     * 사용자 알림 목록 조회 (최근 10개)
     */
    public Map<String, Object> getUserNotifications(Long userIdx) {
        Map<String, Object> userNotifs = userNotifications.getOrDefault(userIdx, new HashMap<>());
        
        // 시간순 정렬해서 최근 10개만 반환
        return userNotifs.entrySet().stream()
                .sorted((e1, e2) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> n1 = (Map<String, Object>) e1.getValue();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> n2 = (Map<String, Object>) e2.getValue();
                    
                    LocalDateTime t1 = (LocalDateTime) n1.get("timestamp");
                    LocalDateTime t2 = (LocalDateTime) n2.get("timestamp");
                    
                    return t2.compareTo(t1); // 최신순
                })
                .limit(10)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1, // 중복키 처리
                    LinkedHashMap::new // 순서 유지
                ));
    }
    
    /**
     * 특정 알림 읽음 처리
     */
    public boolean markAsRead(Long userIdx, String notificationId) {
        Map<String, Object> userNotifs = userNotifications.get(userIdx);
        if (userNotifs != null && userNotifs.containsKey(notificationId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) userNotifs.get(notificationId);
            notification.put("read", true);
            
            log.info("✅ 알림 읽음 처리 - 사용자: {}, 알림ID: {}", userIdx, notificationId);
            return true;
        }
        return false;
    }
    
    /**
     * 모든 알림 읽음 처리
     */
    public void markAllAsRead(Long userIdx) {
        Map<String, Object> userNotifs = userNotifications.get(userIdx);
        if (userNotifs != null) {
            userNotifs.values().forEach(notifObj -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> notification = (Map<String, Object>) notifObj;
                notification.put("read", true);
            });
            
            log.info("✅ 모든 알림 읽음 처리 - 사용자: {}", userIdx);
        }
    }
    
    /**
     * 읽지 않은 알림 개수
     */
    public int getUnreadCount(Long userIdx) {
        Map<String, Object> userNotifs = userNotifications.get(userIdx);
        if (userNotifs == null) return 0;
        
        return (int) userNotifs.values().stream()
                .map(n -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> notification = (Map<String, Object>) n;
                    return notification;
                })
                .filter(n -> !Boolean.TRUE.equals(n.get("read")))
                .count();
    }
    
    /**
     * 알림 삭제
     */
    public boolean deleteNotification(Long userIdx, String notificationId) {
        Map<String, Object> userNotifs = userNotifications.get(userIdx);
        if (userNotifs != null) {
            Object removed = userNotifs.remove(notificationId);
            log.info("🗑️ 알림 삭제 - 사용자: {}, 알림ID: {}, 성공: {}", userIdx, notificationId, removed != null);
            return removed != null;
        }
        return false;
    }
    
    /**
     * WebSocket 실시간 알림 전송
     */
    private void sendRealTimeNotification(Long userIdx, Map<String, Object> notification) {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSendToUser(
                    userIdx.toString(), 
                    "/queue/notifications", 
                    notification
                );
                log.info("📱 실시간 알림 전송 완료 - 사용자: {}, 제목: {}", userIdx, notification.get("title"));
            } catch (Exception e) {
                log.error("📱 실시간 알림 전송 실패 - 사용자: {}, 오류: {}", userIdx, e.getMessage());
            }
        } else {
            log.warn("📱 WebSocket 연결이 없어 실시간 알림 전송 불가 - 사용자: {}", userIdx);
        }
    }
    
    /**
     * 테스트 알림 전송
     */
    public void sendTestNotification(Long userIdx, String title, String message) {
        sendScheduleNotification(userIdx, title != null ? title : "테스트 알림", 
                               message != null ? message : "알림 시스템 테스트입니다!", 
                               LocalDateTime.now().plusMinutes(5));
    }
    
    /**
     * 챗봇 알림 전송
     */
    public void sendChatbotNotification(Long userIdx, String title, String message) {
        try {
            String notificationId = "chatbot_" + System.currentTimeMillis();
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", notificationId);
            notification.put("type", "chatbot");
            notification.put("title", "🤖 " + title);
            notification.put("message", message);
            notification.put("timestamp", LocalDateTime.now());
            notification.put("read", false);
            
            userNotifications.computeIfAbsent(userIdx, k -> new ConcurrentHashMap<>())
                            .put("latest_" + System.currentTimeMillis(), notification);
            
            log.info("🤖 챗봇 알림 저장 완료 - 사용자: {}, 제목: {}", userIdx, title);
            
            sendRealTimeNotification(userIdx, notification);
            
        } catch (Exception e) {
            log.error("🤖 챗봇 알림 저장 실패 - 사용자: {}, 오류: {}", userIdx, e.getMessage(), e);
        }
    }
}