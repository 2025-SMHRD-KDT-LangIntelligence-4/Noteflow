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
    private Long categoryId;

    @Column(name = "large_category", nullable = false, length = 50)
    private String largeCategory;

    @Column(name = "medium_category", nullable = false, length = 100)
    private String mediumCategory;

    @Column(name = "small_category", nullable = false, length = 150)
    private String smallCategory;

    @Column(name = "example_tag", nullable = false, length = 200)
    private String exampleTag;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
