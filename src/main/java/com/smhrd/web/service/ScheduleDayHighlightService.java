// /service/DayHighlightService.java
package com.smhrd.web.service;

import com.smhrd.web.entity.ScheduleDayHighlight;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleDayHighlightRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleDayHighlightService {
  private final ScheduleDayHighlightRepository repo;
  private final UserRepository userRepo;

  public Map<LocalDate, ScheduleDayHighlight> list(Long userIdx, LocalDate start, LocalDate end) {
    Map<LocalDate, ScheduleDayHighlight> map = new HashMap<>();
    for (ScheduleDayHighlight h : repo.findByUser_UserIdxAndDateBetween(userIdx, start, end)) {
      map.put(h.getDate(), h);
    }
    return map;
  }

  public ScheduleDayHighlight upsert(Long userIdx, LocalDate date, String symbol, String color, String note) {
    User user = userRepo.findByUserIdx(userIdx)
      .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    ScheduleDayHighlight h = repo.findByUser_UserIdxAndDate(userIdx, date)
      .orElse(ScheduleDayHighlight.builder().user(user).date(date).build());
    h.setSymbol(symbol);
    h.setColor(color);
    h.setNote(note);
    return repo.save(h);
  }

  public void delete(Long userIdx, LocalDate date) {
    repo.deleteByUser_UserIdxAndDate(userIdx, date);
  }
}
