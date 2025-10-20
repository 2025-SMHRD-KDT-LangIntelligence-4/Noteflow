package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prompt_bundles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "bundle_name", nullable = false)
    private String bundleName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
