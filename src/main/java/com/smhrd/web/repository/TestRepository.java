package com.smhrd.web.repository;

import com.smhrd.web.entity.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestRepository extends JpaRepository<Test, Long> {

    // ===== 기본 조회 =====
    Optional<Test> findByTestIdx(Long testIdx);

    // ===== 제목 검색 =====
    List<Test> findByTestTitleContaining(String keyword);

    @Query("SELECT t FROM Test t WHERE LOWER(t.testTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Test> searchByTitle(@Param("keyword") String keyword);

    // ===== 정렬 =====
    List<Test> findAllByOrderByCreatedAtDesc();

    Page<Test> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ===== 최근 시험 조회 =====
    @Query("SELECT t FROM Test t ORDER BY t.createdAt DESC")
    List<Test> findRecentTests(Pageable pageable);

    // ===== 통계 =====
    @Query("SELECT COUNT(t) FROM Test t")
    long countAllTests();
}
