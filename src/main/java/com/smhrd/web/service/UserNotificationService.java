package com.smhrd.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.NotificationQueue;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.NotificationQueueRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationService {
    
    private final UserRepository userRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final ObjectMapper objectMapper;
    
    // 비활성 사용자 알림 (일주일)
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkInactiveUsers() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        List<User> inactiveUsers = userRepository.findUsersNotLoginSince(oneWeekAgo);
        
        for (User user : inactiveUsers) {
            if (Boolean.TRUE.equals(user.getMailingAgreed()) && Boolean.TRUE.equals(user.getEmailVerified())) {
                scheduleNotification(user.getUserIdx(), "EMAIL", "INACTIVE_USER_WEEK", 
                    Map.of("nickname", user.getNickname()), LocalDateTime.now());
            }
        }
    }
    
    // 파일 삭제 경고 알림 (15일)
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkFileDeletionWarning() {
        LocalDateTime fifteenDaysAgo = LocalDateTime.now().minusDays(15);
        List<User> users = userRepository.findUsersNotLoginSince(fifteenDaysAgo);
        
        for (User user : users) {
            if (Boolean.TRUE.equals(user.getMailingAgreed()) && Boolean.TRUE.equals(user.getEmailVerified())) {
                scheduleNotification(user.getUserIdx(), "EMAIL", "FILE_DELETION_WARNING",
                    Map.of("nickname", user.getNickname()), LocalDateTime.now());
            }
        }
    }
    
    // 파일 삭제 완료 알림 (30일)
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkFileDeletion() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<User> users = userRepository.findUsersNotLoginSince(thirtyDaysAgo);
        
        for (User user : users) {
            if (Boolean.TRUE.equals(user.getMailingAgreed()) && Boolean.TRUE.equals(user.getEmailVerified())) {
                scheduleNotification(user.getUserIdx(), "EMAIL", "FILE_DELETED",
                    Map.of("nickname", user.getNickname()), LocalDateTime.now());
            }
        }
    }
    
    // 개인화된 학습 콘텐츠 발송 (매일 오전 9시)
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendDailyLearningContent() {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        List<User> activeUsers = userRepository.findActiveUsersWithMailingConsent(threeDaysAgo);
        
        for (User user : activeUsers) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nickname", user.getNickname());
            payload.put("interestArea", user.getInterestArea());
            payload.put("learningArea", user.getLearningArea());
            
            scheduleNotification(user.getUserIdx(), "EMAIL", "DAILY_LEARNING_CONTENT", 
                payload, LocalDateTime.now());
        }
    }
    
    private void scheduleNotification(Long userIdx, String channel, String templateCode, 
                                    Map<String, Object> payload, LocalDateTime sendAt) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            NotificationQueue notification = NotificationQueue.builder()
                    .userIdx(userIdx)
                    .channel(NotificationQueue.Channel.valueOf(channel))
                    .templateCode(templateCode)
                    .payloadJson(payloadJson)
                    .sendAt(sendAt)
                    .status(NotificationQueue.Status.READY)
                    .retryCount(0)
                    .build();
                    
            notificationQueueRepository.save(notification);
            log.info("Scheduled notification for user {} with template {}", userIdx, templateCode);
            
        } catch (Exception e) {
            log.error("Failed to schedule notification for user {}: {}", userIdx, e.getMessage());
        }
    }
}
