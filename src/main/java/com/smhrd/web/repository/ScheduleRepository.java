package com.smhrd.web.repository;

import com.smhrd.web.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // ✅ 특정 유저의 모든 일정 조회 (user_idx 기준)
    List<Schedule> findByUser_UserIdx(Long userIdx);

    // ✅ 특정 유저의 날짜 범위 내 일정 조회 (달력 표시용)
    List<Schedule> findByUser_UserIdxAndStartTimeBetween(Long userIdx, LocalDateTime start, LocalDateTime end);

    // ✅ 제목에 특정 키워드가 포함된 일정 검색
    List<Schedule> findByUser_UserIdxAndTitleContainingIgnoreCase(Long userIdx, String title);

    // ✅ 일정 겹침 조회 (예: 시간 중복 체크)
    List<Schedule> findByUser_UserIdxAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(Long userIdx, LocalDateTime end, LocalDateTime start);
}
