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
        schedule.setCreatedAt(LocalDateTime.now());

        return scheduleRepository.save(schedule);
    }

 // ✅ 2. 일정 전체 조회 (유저별)
    public List<Schedule> getAllSchedulesByUser(long userIdx) {
        return scheduleRepository.findByUser_UserIdx(userIdx);
    }

    // ✅ 3. 특정 기간 일정 조회 (달력용)
    public List<Schedule> getSchedulesForPeriod(long userIdx, LocalDateTime start, LocalDateTime end) {
        return scheduleRepository.findByUser_UserIdxAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(userIdx, end, start);
    }

    // ✅ 4. 일정 검색 (키워드 기반, title만 검색)
    public List<Schedule> searchSchedules(long userIdx, String keyword) {
        return scheduleRepository.findByUser_UserIdxAndTitleContainingIgnoreCase(userIdx, keyword);
    }

    // ✅ 5. 일정 수정
    public Schedule updateSchedule(Long scheduleId, Schedule updatedSchedule) {
        Schedule existing = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정이 존재하지 않습니다."));

        existing.setTitle(updatedSchedule.getTitle());
        existing.setStartTime(updatedSchedule.getStartTime());
        existing.setEndTime(updatedSchedule.getEndTime());
        existing.setColorTag(updatedSchedule.getColorTag());

        return scheduleRepository.save(existing);
    }

    // ✅ 6. 일정 삭제
    public void deleteSchedule(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new IllegalArgumentException("해당 일정이 존재하지 않습니다.");
        }
        scheduleRepository.deleteById(scheduleId);
    }

    // ✅ 7. 특정 일정 단건 조회
    public Optional<Schedule> getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId);
    }
}
