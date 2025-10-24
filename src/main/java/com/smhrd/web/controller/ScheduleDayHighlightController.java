// /controller/DayHighlightController.java
package com.smhrd.web.controller;

import com.smhrd.web.entity.ScheduleDayHighlight;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.ScheduleDayHighlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/day-highlights")
public class ScheduleDayHighlightController {

  private final ScheduleDayHighlightService service;

  // GET /api/day-highlights?start=2025-01-01&end=2025-01-31
  @GetMapping
  public ResponseEntity<Map<String, Object>> list(
    Authentication auth,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
  ){
    Long userIdx = ((CustomUserDetails)auth.getPrincipal()).getUserIdx();
    Map<LocalDate, ScheduleDayHighlight> map = service.list(userIdx, start, end);
    // 프런트 편의상 { "items": { "yyyy-MM-dd": {symbol,color,note} } }
    Map<String,Object> resp = new HashMap<>();
    Map<String,Object> items = new HashMap<>();
    map.forEach((d,h)-> items.put(d.toString(), Map.of(
      "symbol", h.getSymbol(),
      "color", h.getColor(),
      "note",  Optional.ofNullable(h.getNote()).orElse("")
    )));
    resp.put("items", items);
    return ResponseEntity.ok(resp);
  }

  // PUT /api/day-highlights/2025-01-23   body: {symbol,color,note}
  @PutMapping("/{date}")
  public ResponseEntity<Map<String,String>> upsert(
    Authentication auth,
    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
    @RequestBody Map<String, String> body
  ){
    Long userIdx = ((CustomUserDetails)auth.getPrincipal()).getUserIdx();
    service.upsert(userIdx, date, body.getOrDefault("symbol","circle"),
      body.getOrDefault("color","red"), body.getOrDefault("note",""));
    return ResponseEntity.ok(Map.of("message","하이라이트가 저장되었습니다."));
  }

  // DELETE /api/day-highlights/2025-01-23
  @DeleteMapping("/{date}")
  public ResponseEntity<Map<String,String>> delete(
    Authentication auth,
    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ){
    Long userIdx = ((CustomUserDetails)auth.getPrincipal()).getUserIdx();
    service.delete(userIdx, date);
    return ResponseEntity.ok(Map.of("message","하이라이트가 삭제되었습니다."));
  }
}
