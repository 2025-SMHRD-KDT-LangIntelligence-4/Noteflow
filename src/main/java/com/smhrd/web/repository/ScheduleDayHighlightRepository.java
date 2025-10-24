// /repository/DayHighlightRepository.java
package com.smhrd.web.repository;

import com.smhrd.web.entity.ScheduleDayHighlight;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleDayHighlightRepository extends JpaRepository<ScheduleDayHighlight, Long> {
  List<ScheduleDayHighlight> findByUser_UserIdxAndDateBetween(Long userIdx, LocalDate start, LocalDate end);
  Optional<ScheduleDayHighlight> findByUser_UserIdxAndDate(Long userIdx, LocalDate date);
  void deleteByUser_UserIdxAndDate(Long userIdx, LocalDate date);
}
