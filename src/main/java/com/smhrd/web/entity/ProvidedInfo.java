package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "provided_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvidedInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long infoIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_idx", nullable = false)
    private Note note;

    @Column(nullable = false)
    private String infoType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String infoContent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
