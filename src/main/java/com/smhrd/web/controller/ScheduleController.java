package com.smhrd.web.controller;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    // 1. 일정 생성
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule,
                                                   Authentication authentication) {
        String userId = authentication.getName();
        Schedule saved = scheduleService.createSchedule(userId, schedule);
        return ResponseEntity.ok(saved);
    }

    // 2. 일정 전체 조회 (로그인한 사용자 기준)
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<Schedule>> getAllSchedules(Authentication authentication) {
        String userId = authentication.getName();
        List<Schedule> schedules = scheduleService.getAllSchedulesByUser(userId);
        return ResponseEntity.ok(schedules);
    }

    // 3. 특정 기간 일정 조회 (FullCalendar용)
    @GetMapping("/period")
    @ResponseBody
    public ResponseEntity<List<Schedule>> getSchedulesForPeriod(
            Authentication authentication,
            @RequestParam String start, // yyyy-MM-dd
            @RequestParam String end    // yyyy-MM-dd
    ) {
        String userId = authentication.getName();
        LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(end).atTime(23, 59, 59);
        List<Schedule> schedules = scheduleService.getSchedulesForPeriod(userId, startDate, endDate);
        return ResponseEntity.ok(schedules);
    }

    // 4. 일정 검색 (title 기준)
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<Schedule>> searchSchedules(
            Authentication authentication,
            @RequestParam String keyword) {

        String userId = authentication.getName();
        List<Schedule> results = scheduleService.searchSchedules(userId, keyword);
        return ResponseEntity.ok(results);
    }

    // 5. 일정 수정
    @PutMapping("/update/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Schedule> updateSchedule(@PathVariable Long scheduleId,
                                                   @RequestBody Schedule updatedSchedule) {
        Schedule schedule = scheduleService.updateSchedule(scheduleId, updatedSchedule);
        return ResponseEntity.ok(schedule);
    }

    // 6. 일정 삭제
    @DeleteMapping("/delete/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
    }

    // 7. 단일 일정 조회
    @GetMapping("/{scheduleId}")
    @ResponseBody
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
        return scheduleService.getScheduleById(scheduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
