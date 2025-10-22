package com.smhrd.web.service;

import com.smhrd.web.dto.ScheduleRequestDto; 
import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleRepository;
import com.smhrd.web.repository.UserRepository; // UserRepository 임포트 추가
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList; 
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository; // UserRepository 의존성 주입 유지
    
    public List<Schedule> searchSchedulesByTitleOrDesc(long userIdx, String keyword) {
        return scheduleRepository.searchByTitleOrDescription(userIdx, keyword);
    }
    // ✅ 1. 일정 생성 (기존 단일 일정 생성)
    public Schedule createSchedule(long userIdx, Schedule schedule) {
        // userIdx를 받아 서비스에서 User 엔티티를 조회
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
    
    /**
     * ✅ 1-1. 선택 기간 동안 매일 동일한 시간대에 반복 일정을 등록합니다.
     * 컨트롤러와 동일한 패턴을 위해 User 엔티티 대신 userIdx를 매개변수로 받습니다.
     * @param userIdx 현재 인증된 사용자 식별자
     * @param requestDto 일정 정보 DTO (반복 기간 및 시간 정보를 포함)
     * @return 생성된 일정 목록
     */
    public List<Schedule> addRepeatSchedules(long userIdx, ScheduleRequestDto requestDto) {
        
        // 1. userIdx를 이용해 User 엔티티를 서비스 내에서 조회
        User user = userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 2. 기간 및 시간 정보 추출
        LocalDate periodStartDate = requestDto.getStartTime().toLocalDate();
        LocalDate periodEndDate = requestDto.getEndTime().toLocalDate();
        
        // 각 일정에 적용할 시간대 (반복되는 시간)
        LocalTime dailyStartTime = requestDto.getStartTime().toLocalTime();
        LocalTime dailyEndTime = requestDto.getEndTime().toLocalTime();

        List<Schedule> createdSchedules = new ArrayList<>();
        LocalDate currentDate = periodStartDate;

        // 시작 날짜부터 종료 날짜까지 하루씩 반복하며 일정 생성 (종료 날짜 포함)
        while (!currentDate.isAfter(periodEndDate)) {
            
            // 3. 단일 일정의 시작/종료 시간 계산
            LocalDateTime scheduleStartTime = currentDate.atTime(dailyStartTime);
            LocalDateTime scheduleEndTime = currentDate.atTime(dailyEndTime);
            
            // 4. 알림 시간 재계산 
            LocalDateTime newAlarmTime = null;
            if (requestDto.getAlarmTime() != null) {
                // 기존 DTO의 alarmTime과 startTime의 차이를 계산 (분 단위)
                long minutesDifference = java.time.Duration.between(requestDto.getStartTime(), requestDto.getAlarmTime()).toMinutes();
                
                // 새로운 일정 시작 시간(scheduleStartTime)에 그 차이만큼 더하거나 뺍니다.
                newAlarmTime = scheduleStartTime.plusMinutes(minutesDifference);
            }
            
            // 5. Schedule 엔티티 생성 (Builder 패턴 사용 가정)
            Schedule schedule = Schedule.builder()
                    .title(requestDto.getTitle())
                    .description(requestDto.getDescription())
                    .colorTag(requestDto.getColorTag())
                    .isAllDay(requestDto.getIsAllDay())
                    .startTime(scheduleStartTime) // 각 날짜의 시작 시간
                    .endTime(scheduleEndTime)     // 각 날짜의 종료 시간
                    
                    // 추가 옵션 필드 매핑
                    .emoji(requestDto.getEmoji())
                    .alarmTime(newAlarmTime)
                    .alertType(requestDto.getAlertType())
                    .customAlertValue(requestDto.getCustomAlertValue())
                    .customAlertUnit(requestDto.getCustomAlertUnit()) 
                    .location(requestDto.getLocation())
                    .mapLat(requestDto.getMapLat())
                    .mapLng(requestDto.getMapLng())
                    .highlightType(requestDto.getHighlightType())
                    .category(requestDto.getCategory())
                    .attachmentPath(requestDto.getAttachmentPath())
                    .attachmentList(requestDto.getAttachmentList())
                    .isAllDay(requestDto.getIsAllDay()) 

                    // 기본값 및 사용자 정보 설정
                    .isDeleted(false) 
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .user(user) // 위에서 조회한 User 엔티티 사용
                    .build();

            // 6. 저장 및 리스트에 추가
            createdSchedules.add(scheduleRepository.save(schedule));
            
            // 다음 날짜로 이동
            currentDate = currentDate.plusDays(1);
        }

        return createdSchedules;
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
