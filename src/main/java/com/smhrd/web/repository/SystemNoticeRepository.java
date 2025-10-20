package com.smhrd.web.repository;

import com.smhrd.web.entity.SystemNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {
    List<SystemNotice> findByIsActiveTrueOrderByCreatedAtDesc();

    @Query("SELECT s FROM SystemNotice s WHERE s.isActive = true " +
            "AND (s.startDate IS NULL OR s.startDate <= :now) " +
            "AND (s.endDate IS NULL OR s.endDate >= :now) " +
            "ORDER BY s.createdAt DESC")
    List<SystemNotice> findActiveNotices(LocalDateTime now);

    List<SystemNotice> findByNoticeTypeAndIsActiveTrue(SystemNotice.NoticeType noticeType);
}
