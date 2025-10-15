package com.smhrd.web.repository;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // ✅ 특정 유저의 모든 일정 조회
    List<Schedule> findByUser(User user);

    // ✅ 특정 유저의 날짜 범위 내 일정 조회 (달력 표시용)
    List<Schedule> findByUserAndStartTimeBetween(User user, LocalDateTime start, LocalDateTime end);

    // ✅ 제목에 특정 키워드가 포함된 일정 검색 (description 제거 반영)
    List<Schedule> findByUserAndTitleContainingIgnoreCase(User user, String title);
 // ScheduleRepository.java 에 추가
    List<Schedule> findByUserAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User user, LocalDateTime end, LocalDateTime start);

}
