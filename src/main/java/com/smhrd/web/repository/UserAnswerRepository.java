package com.smhrd.web.repository;

import com.smhrd.web.entity.TestResult;
import com.smhrd.web.entity.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    // ===== 시험 결과별 답안 조회 =====
    List<UserAnswer> findByResultResultIdx(Long resultIdx);

    List<UserAnswer> findByResultResultIdxOrderByTestSourceTestSourceIdxAsc(Long resultIdx);

    // ===== 특정 문제의 답안 조회 =====
    Optional<UserAnswer> findByResultResultIdxAndTestSourceTestSourceIdx(Long resultIdx, Long testSourceIdx);

    // ===== 정답/오답 조회 =====
    List<UserAnswer> findByResultResultIdxAndIsCorrectTrue(Long resultIdx);

    List<UserAnswer> findByResultResultIdxAndIsCorrectFalse(Long resultIdx);

    // ===== 정답/오답 개수 =====
    @Query("SELECT COUNT(ua) FROM UserAnswer ua " +
            "WHERE ua.result.resultIdx = :resultIdx AND ua.isCorrect = true")
    long countCorrectAnswers(@Param("resultIdx") Long resultIdx);

    @Query("SELECT COUNT(ua) FROM UserAnswer ua " +
            "WHERE ua.result.resultIdx = :resultIdx AND ua.isCorrect = false")
    long countWrongAnswers(@Param("resultIdx") Long resultIdx);

    // ===== 오답노트 (사용자별 오답 문제) =====
    @Query("SELECT ua FROM UserAnswer ua " +
            "WHERE ua.result.user.userIdx = :userIdx " +
            "AND ua.isCorrect = false " +
            "ORDER BY ua.createdAt DESC")
    List<UserAnswer> findWrongAnswersByUser(@Param("userIdx") Long userIdx);

    // ===== 카테고리별 오답 조회 =====
    @Query("SELECT ua FROM UserAnswer ua " +
            "WHERE ua.result.user.userIdx = :userIdx " +
            "AND ua.isCorrect = false " +
            "AND ua.testSource.categoryLarge = :categoryLarge " +
            "ORDER BY ua.createdAt DESC")
    List<UserAnswer> findWrongAnswersByUserAndCategory(
            @Param("userIdx") Long userIdx,
            @Param("categoryLarge") String categoryLarge);

    // ===== 난이도별 오답 조회 =====
    @Query("SELECT ua FROM UserAnswer ua " +
            "WHERE ua.result.user.userIdx = :userIdx " +
            "AND ua.isCorrect = false " +
            "AND ua.testSource.difficulty = :difficulty " +
            "ORDER BY ua.createdAt DESC")
    List<UserAnswer> findWrongAnswersByUserAndDifficulty(
            @Param("userIdx") Long userIdx,
            @Param("difficulty") String difficulty);

    // ===== 특정 문제의 정답률 =====
    @Query("SELECT CAST(SUM(CASE WHEN ua.isCorrect = true THEN 1 ELSE 0 END) AS double) / COUNT(ua) * 100 " +
            "FROM UserAnswer ua WHERE ua.testSource.testSourceIdx = :sourceIdx")
    Double findCorrectRateBySource(@Param("sourceIdx") Long sourceIdx);

    // ===== 사용자의 취약 카테고리 분석 =====
    @Query("SELECT ua.testSource.categoryLarge, " +
            "CAST(SUM(CASE WHEN ua.isCorrect = false THEN 1 ELSE 0 END) AS double) / COUNT(ua) * 100 " +
            "FROM UserAnswer ua " +
            "WHERE ua.result.user.userIdx = :userIdx " +
            "GROUP BY ua.testSource.categoryLarge " +
            "ORDER BY 2 DESC")
    List<Object[]> findWeakCategoriesByUser(@Param("userIdx") Long userIdx);

    // ===== 답안 삭제 (시험 결과 삭제 시) =====
    void deleteByResultResultIdx(Long resultIdx);

	List<UserAnswer> findByResult(TestResult result);
}
