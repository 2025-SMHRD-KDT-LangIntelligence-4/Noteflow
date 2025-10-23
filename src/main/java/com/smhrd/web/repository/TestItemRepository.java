package com.smhrd.web.repository;

import com.smhrd.web.entity.Test;
import com.smhrd.web.entity.TestItem;
import com.smhrd.web.entity.TestResult;
import com.smhrd.web.entity.TestSource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestItemRepository extends JpaRepository<TestItem, Long> {

    // ===== 시험별 문항 조회 =====
    List<TestItem> findByTestTestIdxOrderBySequenceAsc(Long testIdx);

    // ===== 특정 문항 조회 =====
    Optional<TestItem> findByTestTestIdxAndTestSourceTestSourceIdx(Long testIdx, Long testSourceIdx);

    // ===== 문항 개수 조회 =====
    long countByTestTestIdx(Long testIdx);

    // ===== 총 배점 계산 =====
    @Query("SELECT COALESCE(SUM(ti.score), 0) FROM TestItem ti WHERE ti.test.testIdx = :testIdx")
    Integer sumScoreByTestIdx(@Param("testIdx") Long testIdx);

    // ===== 시험 삭제 시 문항도 함께 삭제 (Cascade) =====
    void deleteByTestTestIdx(Long testIdx);

    // ===== 문제 소스별 사용 횟수 조회 =====
    @Query("SELECT COUNT(ti) FROM TestItem ti WHERE ti.testSource.testSourceIdx = :sourceIdx")
    long countByTestSourceIdx(@Param("sourceIdx") Long sourceIdx);

    // ===== 시퀀스 최대값 조회 (다음 순서 번호 계산용) =====
    @Query("SELECT COALESCE(MAX(ti.sequence), 0) FROM TestItem ti WHERE ti.test.testIdx = :testIdx")
    Integer findMaxSequenceByTestIdx(@Param("testIdx") Long testIdx);

	Optional<TestResult> findByTestAndTestSource(Test test, TestSource testSource);
	
	boolean existsByTestTestIdxAndTestSourceTestSourceIdx(Long testIdx, Long testSourceIdx);

    
    List<TestItem> findByTestOrderBySequenceAsc(Test test);
    
}
