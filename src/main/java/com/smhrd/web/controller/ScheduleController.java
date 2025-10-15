package com.smhrd.web.controller;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.dto.ScheduleEventDto;
import com.smhrd.web.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    // --------------------------
    // 1. 일정 생성
    // --------------------------
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule,
                                                   Authentication authentication) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        Schedule saved = scheduleService.createSchedule(userIdx, schedule);
        return ResponseEntity.ok(saved);
    }

    // --------------------------
    // 2. 페이지 진입 시 일정 자동 조회
    // --------------------------
    @GetMapping
    @ResponseBody
    public ResponseEntity<List<ScheduleEventDto>> getSchedulesOnLoad(Authentication authentication) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> schedules = scheduleService.getAllSchedulesByUser(userIdx);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        List<ScheduleEventDto> events = schedules.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(),
                        s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().plusSeconds(1).format(fmt) : null, // end 포함
                        s.getColorTag()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // --------------------------
    // 3. 특정 기간 일정 조회 (FullCalendar용)
    // --------------------------
    @GetMapping("/period")
    @ResponseBody
    public ResponseEntity<List<ScheduleEventDto>> getSchedulesForPeriod(
            Authentication authentication,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();

        List<Schedule> schedules;
        if (start == null || end == null) {
            schedules = scheduleService.getAllSchedulesByUser(userIdx);
        } else {
            LocalDateTime startDate = LocalDate.parse(start.substring(0, 10)).atStartOfDay();
            LocalDateTime endDate = LocalDate.parse(end.substring(0, 10)).atTime(23, 59, 59);
            schedules = scheduleService.getSchedulesForPeriod(userIdx, startDate, endDate);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        List<ScheduleEventDto> events = schedules.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(),
                        s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // --------------------------
    // 4. 일정 검색 (title 기준)
    // --------------------------
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<ScheduleEventDto>> searchSchedules(
            Authentication authentication,
            @RequestParam String keyword) {

        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> results = scheduleService.searchSchedules(userIdx, keyword);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        List<ScheduleEventDto> events = results.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(),
                        s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // --------------------------
    // 5. 일정 수정
    // --------------------------
    @PutMapping("/update/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Schedule> updateSchedule(@PathVariable Long scheduleId,
                                                   @RequestBody Schedule updatedSchedule) {
        Schedule schedule = scheduleService.updateSchedule(scheduleId, updatedSchedule);
        return ResponseEntity.ok(schedule);
    }

    // --------------------------
    // 6. 일정 삭제
    // --------------------------
    @DeleteMapping("/delete/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
    }

    // --------------------------
    // 7. 단일 일정 조회
    // --------------------------
    @GetMapping("/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
        return scheduleService.getScheduleById(scheduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
