package com.smhrd.web.controller;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.dto.ScheduleEventDto;
import com.smhrd.web.dto.ScheduleRequestDto;
import com.smhrd.web.service.ScheduleService;
import com.smhrd.web.service.ScheduleNotificationService;
import com.smhrd.web.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
@Slf4j
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleNotificationService notificationService;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // 1. 일정 생성 (단일 일정)
    @PostMapping("/create")
    public ResponseEntity<Schedule> createSchedule(@RequestBody Schedule schedule, Authentication authentication) {
        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();

        // ✅ alertType에서 email 확인 후 emailNotificationEnabled 설정
        if (schedule.getAlertType() != null && schedule.getAlertType().contains("email")) {
            schedule.setEmailNotificationEnabled(true);
            // customAlertValue가 있으면 notificationMinutesBefore로 사용
            if (schedule.getCustomAlertValue() != null && schedule.getCustomAlertValue() > 0) {
                schedule.setNotificationMinutesBefore(schedule.getCustomAlertValue());
            } else {
                schedule.setNotificationMinutesBefore(0);
            }
        } else {
            schedule.setEmailNotificationEnabled(false);
        }

        // ⭐ 새로 추가: chatbot, web 알림을 웹 알림으로 매핑
        if (schedule.getAlertType() != null && 
            (schedule.getAlertType().contains("chatbot") || schedule.getAlertType().contains("web"))) {
            schedule.setWebNotificationEnabled(true);
            log.info("🔔 웹 알림 활성화: alertType={}", schedule.getAlertType());
        } else {
            schedule.setWebNotificationEnabled(false);
        }

        Schedule savedSchedule = scheduleService.createSchedule(userIdx, schedule);

        // ✅ 이메일 알림 스케줄링
        if (Boolean.TRUE.equals(savedSchedule.getEmailNotificationEnabled())) {
            try {
                notificationService.scheduleNotificationEmail(savedSchedule);
                log.info("✅ 이메일 알림 스케줄링 완료: Schedule ID {}", savedSchedule.getScheduleId());
            } catch (Exception e) {
                log.error("❌ 이메일 알림 스케줄링 실패: {}", e.getMessage(), e);
            }
        }

        // ⭐ 웹 알림 확인 로그
        if (Boolean.TRUE.equals(savedSchedule.getWebNotificationEnabled())) {
            log.info("🔔 웹 알림 설정 완료: Schedule ID {}", savedSchedule.getScheduleId());
        }

        return new ResponseEntity<>(savedSchedule, HttpStatus.CREATED);
    }

    /**
     * ✅ 1-1. 반복 일정 생성 (POST /api/schedule/repeat/add)
     */
    @PostMapping("/repeat/add")
    public ResponseEntity<List<Schedule>> addRepeatSchedule(
            @RequestBody ScheduleRequestDto requestDto,
            Authentication authentication) {
        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();

        if (requestDto.getStartTime() == null || requestDto.getEndTime() == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            List<Schedule> createdSchedules = scheduleService.addRepeatSchedules(userIdx, requestDto);
            if (createdSchedules.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // ✅ 반복 일정 각각에 대해 알림 스케줄링
            for (Schedule schedule : createdSchedules) {
                // 이메일 알림
                if (Boolean.TRUE.equals(schedule.getEmailNotificationEnabled())) {
                    try {
                        notificationService.scheduleNotificationEmail(schedule);
                        log.info("✅ 반복 일정 이메일 알림 스케줄링: Schedule ID {}", schedule.getScheduleId());
                    } catch (Exception e) {
                        log.error("❌ 반복 일정 알림 스케줄링 실패: {}", e.getMessage());
                    }
                }
                
                // ⭐ 웹 알림 확인 로그
                if (Boolean.TRUE.equals(schedule.getWebNotificationEnabled())) {
                    log.info("🔔 반복 일정 웹 알림 설정: Schedule ID {}", schedule.getScheduleId());
                }
            }

            return new ResponseEntity<>(createdSchedules, HttpStatus.CREATED);

        } catch (Exception e) {
            log.error("반복 일정 등록 중 오류 발생: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 2. 일정 조회 통합 (FullCalendar 기간 조회)
    @GetMapping
    public ResponseEntity<List<ScheduleEventDto>> getSchedules(
            Authentication authentication,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> schedules;

        if (start == null || end == null) {
            schedules = scheduleService.getAllSchedulesByUser(userIdx);
        } else {
            try {
                ZonedDateTime startZoned = ZonedDateTime.parse(start);
                ZonedDateTime endZoned = ZonedDateTime.parse(end);
                LocalDateTime startDate = startZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                LocalDateTime endDate = endZoned.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                schedules = scheduleService.getSchedulesForPeriod(userIdx, startDate, endDate);
            } catch (DateTimeParseException e) {
                log.error("날짜 파싱 오류: {}", e.getMessage());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }

        List<ScheduleEventDto> events = schedules.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(), s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag(), s.getDescription(),
                        s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // 4. 일정 검색 (title 기준)
    @GetMapping("/search")
    public ResponseEntity<List<ScheduleEventDto>> searchSchedules(
            Authentication authentication,
            @RequestParam String keyword) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> results = scheduleService.searchSchedules(userIdx, keyword);
        List<ScheduleEventDto> events = results.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(), s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag(), s.getDescription(),
                        s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // 4.1 일정 검색 (제목 + 내용)
    @GetMapping("/search2")
    public ResponseEntity<List<ScheduleEventDto>> searchSchedules2(
            Authentication authentication,
            @RequestParam String keyword) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        List<Schedule> results = scheduleService.searchSchedulesByTitleOrDesc(userIdx, keyword);
        List<ScheduleEventDto> events = results.stream()
                .map(s -> new ScheduleEventDto(
                        s.getScheduleId(), s.getTitle(),
                        s.getStartTime() != null ? s.getStartTime().format(fmt) : null,
                        s.getEndTime() != null ? s.getEndTime().format(fmt) : null,
                        s.getColorTag(), s.getDescription(),
                        s.getIsAllDay(), s.getEmoji(), s.getCategory(), s.getHighlightType()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    // 5. 일정 수정
    @PutMapping("/update/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody Schedule updatedSchedule) {

        // ✅ 기존 일정 조회
        Schedule existingSchedule = scheduleService.getScheduleById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다"));

        boolean wasEmailEnabled = Boolean.TRUE.equals(existingSchedule.getEmailNotificationEnabled());

        // ✅ alertType 기반으로 이메일 알림 설정
        boolean isEmailEnabled = updatedSchedule.getAlertType() != null
                && updatedSchedule.getAlertType().contains("email");

        if (isEmailEnabled) {
            updatedSchedule.setEmailNotificationEnabled(true);
            if (updatedSchedule.getCustomAlertValue() != null && updatedSchedule.getCustomAlertValue() > 0) {
                updatedSchedule.setNotificationMinutesBefore(updatedSchedule.getCustomAlertValue());
            }
        } else {
            updatedSchedule.setEmailNotificationEnabled(false);
        }

        // ⭐ 웹 알림 설정
        boolean isWebEnabled = updatedSchedule.getAlertType() != null
                && (updatedSchedule.getAlertType().contains("chatbot") || updatedSchedule.getAlertType().contains("web"));
                
        if (isWebEnabled) {
            updatedSchedule.setWebNotificationEnabled(true);
            log.info("🔔 웹 알림 활성화 (수정): alertType={}", updatedSchedule.getAlertType());
        } else {
            updatedSchedule.setWebNotificationEnabled(false);
        }

        Schedule schedule = scheduleService.updateSchedule(scheduleId, updatedSchedule);

        // ✅ 이메일 알림 재스케줄링
        if (isEmailEnabled) {
            if (wasEmailEnabled) {
                notificationService.rescheduleNotificationEmail(schedule);
                log.info("✅ 이메일 알림 재스케줄링: Schedule ID {}", scheduleId);
            } else {
                notificationService.scheduleNotificationEmail(schedule);
                log.info("✅ 이메일 알림 신규 스케줄링: Schedule ID {}", scheduleId);
            }
        } else if (wasEmailEnabled) {
            notificationService.cancelNotificationEmail(schedule);
            log.info("✅ 이메일 알림 취소: Schedule ID {}", scheduleId);
        }

        return ResponseEntity.ok(schedule);
    }

    // 6. 일정 삭제 (소프트 삭제)
    @DeleteMapping("/delete/{scheduleId}")
    public ResponseEntity<Map<String, String>> deleteSchedule(@PathVariable Long scheduleId) {
        // ✅ 삭제 전 알림 취소
        scheduleService.getScheduleById(scheduleId).ifPresent(schedule -> {
            if (Boolean.TRUE.equals(schedule.getEmailNotificationEnabled())) {
                notificationService.cancelNotificationEmail(schedule);
                log.info("✅ 삭제된 일정의 이메일 알림 취소: Schedule ID {}", scheduleId);
            }
        });

        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
    }

    // 7. 단일 일정 조회
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getScheduleById(@PathVariable Long scheduleId) {
        return scheduleService.getScheduleById(scheduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 일정 일괄 삭제(소프트)
    @PostMapping(value = "/bulk-delete", consumes = "application/json")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "삭제할 ID가 없습니다."));
        }

        log.info("[bulk-delete] ids={}", ids);

        // ✅ 일괄 삭제 전 알림 취소
        ids.forEach(id -> {
            scheduleService.getScheduleById(id).ifPresent(schedule -> {
                if (Boolean.TRUE.equals(schedule.getEmailNotificationEnabled())) {
                    notificationService.cancelNotificationEmail(schedule);
                }
            });
        });

        int cnt = scheduleService.bulkSoftDelete(ids);
        return ResponseEntity.ok(Map.of("message", cnt + "개 일정 삭제"));
    }

    @PostMapping("/bulk-delete-recent")
    public ResponseEntity<Map<String, Object>> bulkDeleteRecent(
            @RequestParam(defaultValue = "5") int minutes,
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
            m.put("title", s.getTitle());
            m.put("start_time", s.getStartTime() != null ? s.getStartTime().toString() : null);
            m.put("end_time", s.getEndTime() != null ? s.getEndTime().toString() : null);
            m.put("updated_at", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/bulk-restore")
    public ResponseEntity<Map<String, String>> bulkRestore(@RequestBody List<Long> ids) {
        scheduleService.bulkRestore(ids);
        return ResponseEntity.ok(Map.of("message", "복구되었습니다."));
    }

    @GetMapping("/bulk-delete-recent/preview")
    public ResponseEntity<Map<String, Object>> bulkDeleteRecentPreview(
            @RequestParam(defaultValue = "5") int minutes,
            Authentication auth) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        int cnt = scheduleService.countRecentByCreatedAt(userIdx, minutes);
        return ResponseEntity.ok(Map.of("count", cnt));
    }
}