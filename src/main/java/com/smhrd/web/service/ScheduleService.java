package com.smhrd.web.service;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    // ✅ 1. 일정 생성
    public Schedule createSchedule(long userIdx, Schedule schedule) {
        User user = userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        schedule.setUser(user);

        // 생성일시 자동 설정
        if (schedule.getCreatedAt() == null) schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());

        // 엔티티 확장 컬럼 기본값 처리
        if (schedule.getIsAllDay() == null) schedule.setIsAllDay(false);
        if (schedule.getIsDeleted() == null) schedule.setIsDeleted(false);

        return scheduleRepository.save(schedule);
    }

    // ✅ 2. 일정 전체 조회 (삭제되지 않은 일정만)
    public List<Schedule> getAllSchedulesByUser(long userIdx) {
        return scheduleRepository.findByUser_UserIdxAndIsDeletedFalse(userIdx);
    }

    // ✅ 3. 특정 기간 일정 조회 (달력용)
    public List<Schedule> getSchedulesForPeriod(long userIdx, LocalDateTime start, LocalDateTime end) {
        return scheduleRepository.findByUser_UserIdxAndStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndIsDeletedFalse(userIdx, end, start);
    }

    // ✅ 4. 일정 검색 (키워드 기반, title만 검색)
    public List<Schedule> searchSchedules(long userIdx, String keyword) {
        return scheduleRepository.findByUser_UserIdxAndTitleContainingIgnoreCaseAndIsDeletedFalse(userIdx, keyword);
    }

    // ✅ 5. 일정 수정
    public Schedule updateSchedule(Long scheduleId, Schedule updatedSchedule) {
        Schedule existing = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정이 존재하지 않습니다."));

        existing.setTitle(updatedSchedule.getTitle());
        existing.setDescription(updatedSchedule.getDescription());
        existing.setStartTime(updatedSchedule.getStartTime());
        existing.setEndTime(updatedSchedule.getEndTime());
        existing.setColorTag(updatedSchedule.getColorTag());
        existing.setEmoji(updatedSchedule.getEmoji());
        existing.setAlarmTime(updatedSchedule.getAlarmTime());
        existing.setAlertType(updatedSchedule.getAlertType());
        existing.setCustomAlertValue(updatedSchedule.getCustomAlertValue());
        existing.setCustomAlertUnit(updatedSchedule.getCustomAlertUnit());
        existing.setLocation(updatedSchedule.getLocation());
        existing.setMapLat(updatedSchedule.getMapLat());
        existing.setMapLng(updatedSchedule.getMapLng());
        existing.setHighlightType(updatedSchedule.getHighlightType());
        existing.setCategory(updatedSchedule.getCategory());
        existing.setAttachmentPath(updatedSchedule.getAttachmentPath());
        existing.setAttachmentList(updatedSchedule.getAttachmentList());
        existing.setIsAllDay(updatedSchedule.getIsAllDay());
        existing.setUpdatedAt(LocalDateTime.now());

        return scheduleRepository.save(existing);
    }

    // ✅ 6. 일정 삭제 (소프트 삭제)
    public void deleteSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정이 존재하지 않습니다."));
        schedule.setIsDeleted(true);
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);
    }

    // ✅ 7. 특정 일정 단건 조회
    public Optional<Schedule> getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .filter(s -> s.getIsDeleted() == null || !s.getIsDeleted());
    }
}
