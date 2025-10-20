package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(name = "note_views")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "view_id")
    private Long viewId;

    @Column(name = "note_idx", nullable = false)
    private Long noteIdx;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    @PrePersist
    protected void onCreate() {
        this.viewedAt = LocalDateTime.now();
    }
}

