package com.smhrd.web.repository;

import com.smhrd.web.entity.CategoryHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 카테고리 계층 Repository
 * - 공개 카테고리 (user_idx = NULL): 모든 사용자가 볼 수 있음
 * - 개인 카테고리 (user_idx = 특정값): 해당 사용자만 볼 수 있음
 * - 공개 설정 카테고리 (is_public = true): 누구나 볼 수 있음
 */
@Repository
public interface CategoryHierarchyRepository extends JpaRepository<CategoryHierarchy, Long> {

    // ========================================
    // 1. 공개 카테고리 조회 (로그인 불필요)
    // ========================================

    /**
     * 대분류 목록 조회 (공개만)
     */
    @Query("SELECT DISTINCT c.largeCategory FROM CategoryHierarchy c " +
            "WHERE c.userIdx IS NULL OR c.isPublic = true " +
            "ORDER BY c.largeCategory")
    List<String> findDistinctLargeCategories();

    /**
     * 중분류 목록 조회 (공개만)
     */
    @Query("SELECT DISTINCT c.mediumCategory FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND (c.userIdx IS NULL OR c.isPublic = true) " +
            "ORDER BY c.mediumCategory")
    List<String> findDistinctMediumCategories(@Param("largeCategory") String largeCategory);

    /**
     * 소분류 목록 조회 (공개만)
     */
    @Query("SELECT DISTINCT c.smallCategory FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND c.mediumCategory = :mediumCategory " +
            "AND (c.userIdx IS NULL OR c.isPublic = true) " +
            "ORDER BY c.smallCategory")
    List<String> findDistinctSmallCategories(
            @Param("largeCategory") String largeCategory,
            @Param("mediumCategory") String mediumCategory
    );

    // ========================================
    // 2. 유저별 카테고리 조회 (공개 + 본인 것)
    // ========================================

    /**
     * 대분류 목록 조회 (공개 + 본인 카테고리)
     */
    @Query("SELECT DISTINCT c.largeCategory FROM CategoryHierarchy c " +
            "WHERE c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true " +
            "ORDER BY c.largeCategory")
    List<String> findDistinctLargeCategoriesForUser(@Param("userIdx") Long userIdx);

    /**
     * 중분류 목록 조회 (공개 + 본인 카테고리)
     */
    @Query("SELECT DISTINCT c.mediumCategory FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true) " +
            "ORDER BY c.mediumCategory")
    List<String> findDistinctMediumCategoriesForUser(
            @Param("largeCategory") String largeCategory,
            @Param("userIdx") Long userIdx
    );

    /**
     * 소분류 목록 조회 (공개 + 본인 카테고리)
     */
    @Query("SELECT DISTINCT c.smallCategory FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND c.mediumCategory = :mediumCategory " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true) " +
            "ORDER BY c.smallCategory")
    List<String> findDistinctSmallCategoriesForUser(
            @Param("largeCategory") String largeCategory,
            @Param("mediumCategory") String mediumCategory,
            @Param("userIdx") Long userIdx
    );

    // ========================================
    // 3. 키워드 검색
    // ========================================

    /**
     * 키워드 검색 (공개만)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.keywords LIKE %:keyword% " +
            "AND (c.userIdx IS NULL OR c.isPublic = true)")
    List<CategoryHierarchy> findByKeywordsContaining(@Param("keyword") String keyword);

    /**
     * 키워드 검색 (소문자 변환, 공개만)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE LOWER(c.keywords) LIKE %:keyword% " +
            "AND (c.userIdx IS NULL OR c.isPublic = true)")
    List<CategoryHierarchy> findByKeyword(@Param("keyword") String keyword);

    /**
     * 키워드 검색 (유저별)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE LOWER(c.keywords) LIKE %:keyword% " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true)")
    List<CategoryHierarchy> findByKeywordForUser(
            @Param("keyword") String keyword,
            @Param("userIdx") Long userIdx
    );

    /**
     * 복수 키워드 검색 (default 메소드)
     */
    default List<CategoryHierarchy> findCandidatesByKeywords(Set<String> keywords) {
        List<CategoryHierarchy> results = new ArrayList<>();
        for (String keyword : keywords) {
            results.addAll(findByKeyword(keyword.toLowerCase()));
        }
        return results.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 복수 키워드 검색 (유저별)
     */
    default List<CategoryHierarchy> findCandidatesByKeywordsForUser(Set<String> keywords, Long userIdx) {
        List<CategoryHierarchy> results = new ArrayList<>();
        for (String keyword : keywords) {
            results.addAll(findByKeywordForUser(keyword.toLowerCase(), userIdx));
        }
        return results.stream().distinct().collect(Collectors.toList());
    }

    // ========================================
    // 4. 특정 카테고리 조회 (폴더 찾기용)
    // ========================================

    /**
     * 대분류만 조회 (공개 + 본인 것)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true) " +
            "ORDER BY c.categoryId ASC")
    List<CategoryHierarchy> findByLargeCategoryForUser(
            @Param("largeCategory") String largeCategory,
            @Param("userIdx") Long userIdx
    );

    /**
     * 대+중분류 조회 (공개 + 본인 것)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND c.mediumCategory = :mediumCategory " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true) " +
            "ORDER BY c.categoryId ASC")
    List<CategoryHierarchy> findByLargeCategoryAndMediumCategoryForUser(
            @Param("largeCategory") String largeCategory,
            @Param("mediumCategory") String mediumCategory,
            @Param("userIdx") Long userIdx
    );

    /**
     * 대+중+소 조회 (공개 + 본인 것)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :largeCategory " +
            "AND c.mediumCategory = :mediumCategory " +
            "AND c.smallCategory = :smallCategory " +
            "AND (c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true)")
    Optional<CategoryHierarchy> findByLargeCategoryAndMediumCategoryAndSmallCategoryForUser(
            @Param("largeCategory") String largeCategory,
            @Param("mediumCategory") String mediumCategory,
            @Param("smallCategory") String smallCategory,
            @Param("userIdx") Long userIdx
    );

    // ========================================
    // 5. 중복 체크 (카테고리 추가 시)
    // ========================================

    /**
     * 특정 유저의 특정 카테고리 조합 존재 여부
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :large " +
            "AND c.mediumCategory = :medium " +
            "AND c.smallCategory = :small " +
            "AND c.userIdx = :userIdx")
    Optional<CategoryHierarchy> findByLargeCategoryAndMediumCategoryAndSmallCategoryAndUserIdx(
            @Param("large") String large,
            @Param("medium") String medium,
            @Param("small") String small,
            @Param("userIdx") Long userIdx
    );

    /**
     * 공개 카테고리 중복 체크
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.largeCategory = :large " +
            "AND c.mediumCategory = :medium " +
            "AND c.smallCategory = :small " +
            "AND c.userIdx IS NULL")
    Optional<CategoryHierarchy> findByLargeCategoryAndMediumCategoryAndSmallCategory(
            @Param("large") String large,
            @Param("medium") String medium,
            @Param("small") String small
    );

    // ========================================
    // 6. 전체 조회 (유저별 필터링)
    // ========================================

    /**
     * 전체 카테고리 조회 (공개 + 본인 것)
     */
    @Query("SELECT c FROM CategoryHierarchy c " +
            "WHERE c.userIdx IS NULL OR c.userIdx = :userIdx OR c.isPublic = true " +
            "ORDER BY c.largeCategory, c.mediumCategory, c.smallCategory")
    List<CategoryHierarchy> findAllForUser(@Param("userIdx") Long userIdx);
}
