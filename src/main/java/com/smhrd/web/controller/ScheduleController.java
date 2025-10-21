package com.smhrd.web.controller;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.dto.ScheduleEventDto;
import com.smhrd.web.service.ScheduleService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId; // ✅ 추가: ZonedDateTime 사용을 위해
import java.time.ZonedDateTime; // ✅ 추가: ZonedDateTime 사용을 위해
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException; // ✅ 추가: 예외 처리
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // 1. 일정 생성 (이전 코드로 롤백: Schedule 엔티티를 직접 받음)
    @PostMapping("/create")
    public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule,
                                                   Authentication authentication) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        Schedule savedSchedule = scheduleService.createSchedule(userIdx, schedule);
        return new ResponseEntity<>(savedSchedule, HttpStatus.CREATED);
    }
    
    // 2. 일정 조회 통합 (FullCalendar 기간 조회)
    // ✅ getSchedulesOnLoad와 getSchedulesForPeriod를 이 메서드로 통합
    @GetMapping 
    public ResponseEntity<List<ScheduleEventDto>> getSchedules(
            Authentication authentication,
            @RequestParam(required = false) String start, // FullCalendar의 start (기간 시작일)
            @RequestParam(required = false) String end    // FullCalendar의 end (기간 종료일 다음 날)
    ) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();

        List<Schedule> schedules;
        
        if (start == null || end == null) {
             // 2-1. start/end 파라미터가 없으면 전체 일정 로드 (페이지 진입 시)
            schedules = scheduleService.getAllSchedulesByUser(userIdx);
        } else {
             // 2-2. start/end 파라미터가 있으면 기간 조회 (FullCalendar 뷰 변경/이동 시)
            try {
                // ✅ 핵심 수정: Time Zone 정보가 포함된 문자열을 ZonedDateTime으로 파싱
                ZonedDateTime startZoned = ZonedDateTime.parse(start);
                ZonedDateTime endZoned = ZonedDateTime.parse(end);

                // ✅ KST (시스템 기본 Time Zone)의 LocalDateTime으로 변환하여 서비스에 전달
                // 이 변환은 FullCalendar가 보낸 시간을 DB의 KST 기준 LocalDateTime과 비교 가능하도록 합니다.
                LocalDateTime startDate = startZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                LocalDateTime endDate = endZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

                // 서비스 호출 (EndDate는 FullCalendar에서 다음 날 00:00:00으로 보내므로 그대로 사용)
                schedules = scheduleService.getSchedulesForPeriod(userIdx, startDate, endDate);

            } catch (DateTimeParseException e) {
                // 파싱 오류 발생 시 로그 기록 후 빈 목록 또는 에러 반환
                System.err.println("날짜 파싱 오류: " + e.getMessage());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }

        List<ScheduleEventDto> events = schedules.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(),
                        s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag(),
                        s.getDescription(),
                        s.getIsAllDay(),
                        s.getEmoji()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // 🔴 getSchedulesOnLoad 메서드 삭제 (위 getSchedules로 통합됨)

    // 🔴 getSchedulesForPeriod 메서드 삭제 (위 getSchedules로 통합됨)
    // 이전 코드를 유지하고 싶다면, @GetMapping("/period")로 매핑을 유지해야 합니다.
    // 하지만 충돌을 막기 위해 getSchedulesOnLoad를 삭제하고 getSchedulesForPeriod의 로직을 getSchedules로 옮기는 통합을 권장합니다.

    // 4. 일정 검색 (title 기준)
    @GetMapping("/search") // 기존 매핑 유지
    public ResponseEntity<List<ScheduleEventDto>> searchSchedules(
            Authentication authentication,
            @RequestParam String keyword) {

        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> results = scheduleService.searchSchedules(userIdx, keyword);

        List<ScheduleEventDto> events = results.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(),
                        s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag(),
                        s.getDescription(),
                        s.getIsAllDay(),
                        s.getEmoji()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // 5. 일정 수정 (기존 코드로 롤백)
    @PutMapping("/update/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(@PathVariable Long scheduleId,
                                                   @RequestBody Schedule updatedSchedule) {
        Schedule schedule = scheduleService.updateSchedule(scheduleId, updatedSchedule);
        return ResponseEntity.ok(schedule);
    }

    // 6. 일정 삭제 (기존 코드 유지)
    @DeleteMapping("/delete/{scheduleId}")
    public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
    }

    // 7. 단일 일정 조회 (기존 코드 유지)
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
        return scheduleService.getScheduleById(scheduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}