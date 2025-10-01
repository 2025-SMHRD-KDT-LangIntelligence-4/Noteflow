package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attachmentIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_idx")
    private Note note;

    private String fileName;
    private String filePath;
    private String fileType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
