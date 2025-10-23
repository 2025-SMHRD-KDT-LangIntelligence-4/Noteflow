package com.smhrd.web.controller;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.dto.ScheduleEventDto;
import com.smhrd.web.dto.ScheduleRequestDto; // ✅ 추가: 반복 일정 등록을 위해 DTO 임포트
import com.smhrd.web.service.ScheduleService;
import com.smhrd.web.security.CustomUserDetails; // CustomUserDetails 명시적 임포트
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap; // 선택사항(풀패스 써서 위에선 생략 가능)

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleController {

	private final ScheduleService scheduleService;
	private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	// 1. 일정 생성 (단일 일정)
	@PostMapping("/create")
	public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule, Authentication authentication) {
		Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
		Schedule savedSchedule = scheduleService.createSchedule(userIdx, schedule);
		return new ResponseEntity<>(savedSchedule, HttpStatus.CREATED);
	}

	/**
	 * ✅ 1-1. 반복 일정 생성 (POST /api/schedule/repeat/add) ScheduleRequestDto를 받아 기간 내
	 * 매일 동일한 시간의 일정을 생성합니다.
	 */
	@PostMapping("/repeat/add")
	public ResponseEntity<List<Schedule>> addRepeatSchedule(@RequestBody ScheduleRequestDto requestDto,
			Authentication authentication) {

		Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();

		// 필수 파라미터 유효성 검사 (시작/종료 시간은 기간 정보를 포함)
		if (requestDto.getStartTime() == null || requestDto.getEndTime() == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		try {
			// 서비스 계층 호출 (서비스는 userIdx와 DTO를 이용해 반복 일정 목록 생성)
			// 주의: 서비스 계층은 userIdx를 받아 내부적으로 User 엔티티를 찾아야 합니다.
			List<Schedule> createdSchedules = scheduleService.addRepeatSchedules(userIdx, requestDto);

			if (createdSchedules.isEmpty()) {
				// 생성된 일정이 없다면 (일반적으로 발생하지 않으나, 기간이 0일이거나 오류 발생 시)
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}

			// 성공 시 201 Created와 생성된 일정 목록 반환
			return new ResponseEntity<>(createdSchedules, HttpStatus.CREATED);

		} catch (Exception e) {
			System.err.println("반복 일정 등록 중 오류 발생: " + e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // 500
		}
	}

	// 2. 일정 조회 통합 (FullCalendar 기간 조회)
	@GetMapping
	public ResponseEntity<List<ScheduleEventDto>> getSchedules(Authentication authentication,
			@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
		Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();

		List<Schedule> schedules;

		if (start == null || end == null) {
			// 2-1. start/end 파라미터가 없으면 전체 일정 로드 (페이지 진입 시)
			schedules = scheduleService.getAllSchedulesByUser(userIdx);
		} else {
			// 2-2. start/end 파라미터가 있으면 기간 조회 (FullCalendar 뷰 변경/이동 시)
			try {
				ZonedDateTime startZoned = ZonedDateTime.parse(start);
				ZonedDateTime endZoned = ZonedDateTime.parse(end);

				LocalDateTime startDate = startZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
				LocalDateTime endDate = endZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

				schedules = scheduleService.getSchedulesForPeriod(userIdx, startDate, endDate);

			} catch (DateTimeParseException e) {
				System.err.println("날짜 파싱 오류: " + e.getMessage());
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
		}

		List<ScheduleEventDto> events = schedules.stream()
				.map(s -> new ScheduleEventDto(s.getScheduleId(), s.getTitle(),
						s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
						s.getEndTime() != null ? s.getEndTime().format(fmt) : null, s.getColorTag(), s.getDescription(),
						s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()))
				.collect(Collectors.toList());

		return ResponseEntity.ok(events);
	}

	// 4. 일정 검색 (title 기준)
	@GetMapping("/search")
	public ResponseEntity<List<ScheduleEventDto>> searchSchedules(Authentication authentication,
			@RequestParam String keyword) {

		Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
		List<Schedule> results = scheduleService.searchSchedules(userIdx, keyword);

		List<ScheduleEventDto> events = results.stream()
				.map(s -> new ScheduleEventDto(s.getScheduleId(), s.getTitle(),
						s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
						s.getEndTime() != null ? s.getEndTime().format(fmt) : null, s.getColorTag(), s.getDescription(),
						s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()))
				.collect(Collectors.toList());

		return ResponseEntity.ok(events);
	}

	// 4.1 일정 검색 (제목 + 내용)
	@GetMapping("/search2")
	public ResponseEntity<List<ScheduleEventDto>> searchSchedules2(Authentication authentication,
			@RequestParam String keyword) {
		Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
		List<Schedule> results = scheduleService.searchSchedulesByTitleOrDesc(userIdx, keyword);
		List<ScheduleEventDto> events = results.stream()
				.map(s -> new ScheduleEventDto(s.getScheduleId(), s.getTitle(),
						s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
						s.getEndTime() != null ? s.getEndTime().format(fmt) : null, s.getColorTag(), s.getDescription(),
						s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()))
				.collect(Collectors.toList());
		return ResponseEntity.ok(events);
	}

	// 5. 일정 수정
	@PutMapping("/update/{scheduleId}")
	public ResponseEntity<Schedule> updateSchedule(@PathVariable Long scheduleId,
			@RequestBody Schedule updatedSchedule) {
		Schedule schedule = scheduleService.updateSchedule(scheduleId, updatedSchedule);
		return ResponseEntity.ok(schedule);
	}

	// 6. 일정 삭제 (소프트 삭제)
	@DeleteMapping("/delete/{scheduleId}")
	public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable Long scheduleId) {
		scheduleService.deleteSchedule(scheduleId);
		return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
	}

	// 7. 단일 일정 조회
	@GetMapping("/{scheduleId}")
	public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
		return scheduleService.getScheduleById(scheduleId).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	// 일정 일괄 삭제(소프트)
	@PostMapping(value = "/bulk-delete", consumes = "application/json")
	public ResponseEntity<Map<String, String>> bulkDelete(@RequestBody List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "삭제할 ID가 없습니다."));
		}
		System.out.println("[bulk-delete] ids=" + ids);
		int cnt = scheduleService.bulkSoftDelete(ids);
		return ResponseEntity.ok(Map.of("message", cnt + "개 일정 삭제"));
	}

	@PostMapping("/bulk-delete-recent")
	public ResponseEntity<Map<String, String>> bulkDeleteRecent(@RequestParam(defaultValue = "5") int minutes,
			Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		int cnt = scheduleService.softDeleteRecentByCreatedAt(userIdx, minutes);
		return ResponseEntity.ok(Map.of("message", cnt + "개 삭제"));
	}

	@GetMapping("/trash")
	public ResponseEntity<List<Map<String, Object>>> listTrash(Authentication auth) {
		Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
		List<Schedule> list = scheduleService.findDeletedByUser(userIdx);

		List<Map<String, Object>> dto = list.stream().map(s -> {
			Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("schedule_id", s.getScheduleId());
			m.put("title", s.getTitle()); // null 허용
			m.put("start_time", s.getStartTime() != null ? s.getStartTime().toString() : null);
			m.put("end_time", s.getEndTime() != null ? s.getEndTime().toString() : null);
			m.put("updated_at", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
			return m;
		}).collect(java.util.stream.Collectors.toList()); // Java 8/11 호환
		return ResponseEntity.ok(dto);
	}

	@PostMapping("/bulk-restore")
	public ResponseEntity<Map<String, String>> bulkRestore(@RequestBody List<Long> ids) {
		scheduleService.bulkRestore(ids);
		return ResponseEntity.ok(Map.of("message", "복구되었습니다."));
	}
	@GetMapping("/bulk-delete-recent/preview")
	public ResponseEntity<Map<String, Integer>> bulkDeleteRecentPreview(
	        @RequestParam(defaultValue = "5") int minutes,
	        Authentication auth) {
	    Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
	    int cnt = scheduleService.countRecentByCreatedAt(userIdx, minutes);
	    return ResponseEntity.ok(Map.of("count", cnt));
	}
}
