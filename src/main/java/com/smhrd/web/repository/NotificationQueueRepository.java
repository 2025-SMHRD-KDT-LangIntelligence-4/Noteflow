package com.smhrd.web.repository;

import com.smhrd.web.entity.NotificationQueue;
import com.smhrd.web.entity.NotificationQueue.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {
    
    @Query("SELECT n FROM NotificationQueue n WHERE n.status = :status AND n.sendAt <= :sendTime ORDER BY n.sendAt ASC")
    List<NotificationQueue> findReadyNotifications(Status status, LocalDateTime sendTime);
    
    @Query("SELECT n FROM NotificationQueue n WHERE n.status = 'FAILED' AND n.retryCount < 3")
    List<NotificationQueue> findFailedNotificationsForRetry();
}
