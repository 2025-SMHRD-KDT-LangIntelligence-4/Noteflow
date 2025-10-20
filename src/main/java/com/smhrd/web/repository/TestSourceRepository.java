package com.smhrd.web.repository;

import com.smhrd.web.entity.QuestionType;
import com.smhrd.web.entity.TestSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSourceRepository extends JpaRepository<TestSource, Long> {

    // ===== 카테고리별 조회 =====
    List<TestSource> findByCategoryLarge(String categoryLarge);

    List<TestSource> findByCategoryLargeAndCategoryMedium(String categoryLarge, String categoryMedium);

    List<TestSource> findByCategoryLargeAndCategoryMediumAndCategorySmall(
            String categoryLarge, String categoryMedium, String categorySmall);

    // ===== 난이도별 조회 =====
    List<TestSource> findByDifficulty(String difficulty);

    // ===== 문제 유형별 조회 =====
    List<TestSource> findByQuestionType(QuestionType questionType);

    // ===== 복합 조건 조회 =====
    List<TestSource> findByCategoryLargeAndDifficulty(String categoryLarge, String difficulty);

    List<TestSource> findByCategoryLargeAndCategoryMediumAndDifficulty(
            String categoryLarge, String categoryMedium, String difficulty);

    List<TestSource> findByCategoryLargeAndQuestionType(String categoryLarge, QuestionType questionType);

    List<TestSource> findByCategoryLargeAndDifficultyAndQuestionType(
            String categoryLarge, String difficulty, QuestionType questionType);

    // ===== 랜덤 문제 선택 (Native Query) =====
    @Query(value = "SELECT * FROM test_sources " +
            "WHERE category_large = :categoryLarge " +
            "ORDER BY RAND() LIMIT :limit",
            nativeQuery = true)
    List<TestSource> findRandomByCategoryLarge(
            @Param("categoryLarge") String categoryLarge,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM test_sources " +
            "WHERE category_large = :categoryLarge " +
            "AND difficulty = :difficulty " +
            "ORDER BY RAND() LIMIT :limit",
            nativeQuery = true)
    List<TestSource> findRandomByCategoryLargeAndDifficulty(
            @Param("categoryLarge") String categoryLarge,
            @Param("difficulty") String difficulty,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM test_sources " +
            "WHERE category_large = :categoryLarge " +
            "AND category_medium = :categoryMedium " +
            "ORDER BY RAND() LIMIT :limit",
            nativeQuery = true)
    List<TestSource> findRandomByCategoryLargeAndMedium(
            @Param("categoryLarge") String categoryLarge,
            @Param("categoryMedium") String categoryMedium,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM test_sources " +
            "WHERE category_large = :categoryLarge " +
            "AND difficulty = :difficulty " +
            "AND question_type = :questionType " +
            "ORDER BY RAND() LIMIT :limit",
            nativeQuery = true)
    List<TestSource> findRandomByFilters(
            @Param("categoryLarge") String categoryLarge,
            @Param("difficulty") String difficulty,
            @Param("questionType") String questionType,
            @Param("limit") int limit);

    // ===== 검색 =====
    @Query("SELECT ts FROM TestSource ts WHERE " +
            "LOWER(ts.question) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<TestSource> searchByQuestionKeyword(@Param("keyword") String keyword);

    @Query("SELECT ts FROM TestSource ts WHERE " +
            "LOWER(ts.question) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(ts.categoryLarge) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(ts.categoryMedium) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<TestSource> searchByKeyword(@Param("keyword") String keyword);

    // ===== 통계/카운트 =====
    long countByCategoryLarge(String categoryLarge);

    long countByCategoryLargeAndCategoryMedium(String categoryLarge, String categoryMedium);

    long countByCategoryLargeAndDifficulty(String categoryLarge, String difficulty);

    long countByDifficulty(String difficulty);

    long countByQuestionType(QuestionType questionType);

    // ===== 페이징 조회 =====
    Page<TestSource> findByCategoryLarge(String categoryLarge, Pageable pageable);

    Page<TestSource> findByCategoryLargeAndDifficulty(String categoryLarge, String difficulty, Pageable pageable);
}
