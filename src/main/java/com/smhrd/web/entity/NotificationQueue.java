package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationQueue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_idx", nullable = false)
    private Long userIdx;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;
    
    @Column(name = "template_code", nullable = false)
    private String templateCode;
    
    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;
    
    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.READY;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    public enum Channel {
        EMAIL, DISCORD, TELEGRAM
    }
    
    public enum Status {
        READY, SENT, FAILED
    }
}
