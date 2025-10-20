package com.smhrd.web.repository;

import com.smhrd.web.entity.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, Long> {
    List<Entitlement> findByUserIdx(Long userIdx);

    @Query("SELECT e FROM Entitlement e WHERE e.userIdx = :userIdx " +
            "AND (e.expiresAt IS NULL OR e.expiresAt > :now) " +
            "ORDER BY e.grantedAt DESC")
    List<Entitlement> findActiveByUserIdx(Long userIdx, LocalDateTime now);

    Optional<Entitlement> findFirstByUserIdxAndTierCodeOrderByGrantedAtDesc(Long userIdx, Entitlement.TierCode tierCode);
}
