package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "note_likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"note_idx", "user_idx"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;

    @Column(name = "note_idx", nullable = false)
    private Long noteIdx;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

