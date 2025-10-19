package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "category_hierarchy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryHierarchy {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "large_category", nullable = false, length = 50)
    private String largeCategory;

    @Column(name = "medium_category", nullable = false, length = 100)
    private String mediumCategory;

    @Column(name = "small_category", nullable = false, length = 150)
    private String smallCategory;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "confidence_score")
    private Double confidenceScore;
    // ✅ 추가: 소유자
    @Column(name = "user_idx")
    private Long userIdx;  // NULL이면 공개 카테고리

    // ✅ 추가: 공개 여부
    @Column(name = "is_public")
    private Boolean isPublic = false;  // true: 다른 사용자도 사용 가능

    public double getConfidenceScore() {
        return confidenceScore != null ? confidenceScore : 0.0;
    }

    public void setConfidenceScore(double score) {
        this.confidenceScore = score;

    }

    public String getExampleTag() {
        StringBuilder tag = new StringBuilder("[");
        tag.append(largeCategory != null ? largeCategory : "미분류");

        if (mediumCategory != null && !mediumCategory.trim().isEmpty()) {
            tag.append(" > ").append(mediumCategory);

            if (smallCategory != null && !smallCategory.trim().isEmpty()) {
                tag.append(" > ").append(smallCategory);
            }
        }

        tag.append("]");
        return tag.toString();
    }
}