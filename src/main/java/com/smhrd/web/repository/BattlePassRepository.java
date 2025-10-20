package com.smhrd.web.repository;

import com.smhrd.web.entity.BattlePass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BattlePassRepository extends JpaRepository<BattlePass, Long> {
    Optional<BattlePass> findBySeasonCode(String seasonCode);
    List<BattlePass> findByIsActiveTrueOrderByStartDateDesc();
    Optional<BattlePass> findFirstByIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate start, LocalDate end);
}
