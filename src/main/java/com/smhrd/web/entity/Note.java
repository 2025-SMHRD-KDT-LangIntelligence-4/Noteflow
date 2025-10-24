package com.smhrd.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "noteTags"})  // ✅ noteTags 추가
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noteIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "source_id")
    private String sourceId;
    // ✅ 추가
    @Column(name = "prompt_id")
    private Long promptId;
    
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Boolean isPublic = false;

    @Column(nullable = false)
    private Integer viewCount = 0;

    @Column(nullable = false)
    private Integer likeCount = 0;

    @Column(nullable = false)
    private Integer commentCount = 0;

    @Column(nullable = false)
    private Integer reportCount = 0;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column
    private String blockedReason;

    @Column
    private LocalDateTime blockedAt;

    @Column
    private String aiPromptId;

    @Column
    private Integer aiGenerationTimeMs;

    @Column
    private Integer aiSatisfactionScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "folder_id")
    private Long folderId;

    // ✅ 기존 필드명 변경: tags -> noteTags
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<NoteTag> noteTags;

    // ✅ JSON 직렬화용 임시 필드 추가
    @Transient
    private List<Tag> tags;

    // ✅ Tag 리스트를 위한 getter (JSON용)
    @JsonProperty("tags")
    public List<Tag> getTags() {
        // Service에서 setTags로 설정한 값이 있으면 그거 리턴
        if (this.tags != null) {
            return this.tags;
        }
        // 없으면 noteTags에서 변환
        if (this.noteTags != null) {
            return this.noteTags.stream()
                    .map(NoteTag::getTag)
                    .collect(Collectors.toList());
        }
        return null;
    }

    // ✅ Tag 리스트를 위한 setter (Service용)
    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public void incrementViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        this.viewCount++;
    }
}
