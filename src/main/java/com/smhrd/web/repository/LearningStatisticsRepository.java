package com.smhrd.web.repository;

import com.smhrd.web.entity.LearningStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LearningStatisticsRepository extends JpaRepository<LearningStatistics, Long> {
    Optional<LearningStatistics> findByUserIdxAndStatDate(Long userIdx, LocalDate statDate);
    List<LearningStatistics> findByUserIdxAndStatDateBetweenOrderByStatDateAsc(Long userIdx, LocalDate start, LocalDate end);
    List<LearningStatistics> findByUserIdxOrderByStatDateDesc(Long userIdx);
}
