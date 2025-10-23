package com.smhrd.web.repository;

import com.smhrd.web.entity.TempSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TempScheduleRepository extends JpaRepository<TempSchedule, Long> {

    List<TempSchedule> findByUser_UserIdxAndIsDeletedFalseOrderByUpdatedAtDesc(Long userIdx);

    List<TempSchedule> findByUser_UserIdxAndStartTimeBetweenAndIsDeletedFalse(
            Long userIdx, LocalDateTime from, LocalDateTime to
    );

    @Query("""
           SELECT DATE(ts.startTime) as d, COUNT(ts)
           FROM TempSchedule ts
           WHERE ts.user.userIdx = :userIdx
             AND (ts.isDeleted = false OR ts.isDeleted IS NULL)
             AND ts.startTime BETWEEN :from AND :to
           GROUP BY DATE(ts.startTime)
           """)
    List<Object[]> countGroupByDate(Long userIdx, LocalDateTime from, LocalDateTime to);
}
