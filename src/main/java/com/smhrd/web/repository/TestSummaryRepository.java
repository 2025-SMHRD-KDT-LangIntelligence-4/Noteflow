package com.smhrd.web.repository;

import com.smhrd.web.entity.TestSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TestSummaryRepository extends JpaRepository<TestSummary, Long> {
    List<TestSummary> findByStatusOrderByCreatedAtDesc(String status);
    List<TestSummary> findTop10ByOrderByCreatedAtDesc();
}
