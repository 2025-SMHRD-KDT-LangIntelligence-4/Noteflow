package com.smhrd.web.repository;

import com.smhrd.web.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByUserUserIdxOrderByCreatedAtDesc(Long userIdx);

    Page<TestResult> findByUserUserIdxOrderByCreatedAtDesc(Long userIdx, Pageable pageable);

    List<TestResult> findByTestTestIdxOrderByCreatedAtDesc(Long testIdx);

    Optional<TestResult> findFirstByUserUserIdxOrderByCreatedAtDesc(Long userIdx);

    @Query("SELECT AVG(tr.userScore) FROM TestResult tr WHERE tr.user.userIdx = :userIdx")
    Double findAverageScoreByUser(@Param("userIdx") Long userIdx);

    @Query("SELECT tr FROM TestResult tr WHERE tr.user.userIdx = :userIdx " +
            "ORDER BY tr.createdAt DESC")
    Page<TestResult> findRecentResultsByUser(@Param("userIdx") Long userIdx, Pageable pageable);

    long countByUserUserIdx(Long userIdx);

    long countByUserUserIdxAndPassedTrue(Long userIdx);
    
    
}
