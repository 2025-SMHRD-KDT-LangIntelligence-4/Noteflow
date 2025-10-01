package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "download_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_idx", nullable = false)
    private Attachment attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime downloadedAt;
}
