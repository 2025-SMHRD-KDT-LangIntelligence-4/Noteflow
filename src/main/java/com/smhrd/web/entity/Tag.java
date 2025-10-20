package com.smhrd.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "noteTags"})  // ✅ 추가

public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_idx")
    private Long tagIdx;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;  // ✅ 이 필드가 필요

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "tag")
    @JsonIgnore  // ✅ 추가
    private List<NoteTag> noteTags;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (usageCount == null) {
            usageCount = 0;
        }
    }
}
