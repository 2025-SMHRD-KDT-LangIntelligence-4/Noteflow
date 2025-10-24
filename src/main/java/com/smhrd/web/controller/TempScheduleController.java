package com.smhrd.web.controller;

import com.smhrd.web.dto.ScheduleRequestDto;
import com.smhrd.web.entity.TempSchedule;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.TempScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/temp-schedule")
public class TempScheduleController {

	private final TempScheduleService tempService;

	@PostMapping("") // ← 추가
	public ResponseEntity<Map<String, Object>> createDraft(@RequestBody ScheduleRequestDto dto, Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		TempSchedule saved = tempService.saveDraft(userIdx, dto);
		return ResponseEntity.ok(Map.of("message", "임시 저장되었습니다.", "tempId", saved.getTempId()));
	}

	@PostMapping("/save")
	public ResponseEntity<Map<String, Object>> saveDraft(@RequestBody ScheduleRequestDto dto, Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		TempSchedule saved = tempService.saveDraft(userIdx, dto);
		return ResponseEntity.ok(Map.of("message", "임시 저장되었습니다.", "tempId", saved.getTempId()));
	}
	@PostMapping("/{tempId}") // ← 추가
	public ResponseEntity<Map<String, Object>> updateDraft(
	        @PathVariable Long tempId,
	        @RequestBody ScheduleRequestDto dto,
	        Authentication auth
	) {
	    Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
	    TempSchedule saved = tempService.updateDraft(tempId, userIdx, dto);
	    return ResponseEntity.ok(Map.of(
	            "message", "임시 저장되었습니다.",
	            "tempId", saved.getTempId()
	    ));
	}
	@GetMapping("/list")
	public ResponseEntity<List<Map<String, Object>>> listDrafts(Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		List<TempSchedule> list = tempService.listDrafts(userIdx);
		List<Map<String, Object>> dto = new ArrayList<>();
		for (TempSchedule t : list) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("temp_id", t.getTempId());
			m.put("title", t.getTitle());
			m.put("start_time", t.getStartTime() != null ? t.getStartTime().toString() : null);
			m.put("end_time", t.getEndTime() != null ? t.getEndTime().toString() : null);
			m.put("updated_at", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null);
			dto.add(m);
		}
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/{tempId}")
	public ResponseEntity<TempSchedule> getDraft(@PathVariable Long tempId, Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		return tempService.getDraft(tempId, userIdx).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{tempId}")
	public ResponseEntity<Map<String, String>> deleteDraft(@PathVariable Long tempId, Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		tempService.deleteDraft(tempId, userIdx);
		return ResponseEntity.ok(Map.of("message", "임시 일정이 삭제되었습니다."));
	}

	// 달력 + 버튼에 뱃지(아이콘) 표시를 위한 날짜별 카운트
	@GetMapping("/date-counts")
	public ResponseEntity<Map<String, Long>> countByDate(Authentication auth,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		Map<LocalDate, Long> counts = tempService.countByDate(userIdx, start, end);
		Map<String, Long> resp = new HashMap<>();
		counts.forEach((d, c) -> resp.put(d.toString(), c)); // yyyy-MM-dd -> count
		return ResponseEntity.ok(resp);
	}
}
