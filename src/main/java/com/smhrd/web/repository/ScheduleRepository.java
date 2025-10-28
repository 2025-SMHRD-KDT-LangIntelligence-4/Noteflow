package com.smhrd.web.repository;

import com.smhrd.web.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	// 특정 유저의 모든 일정 조회 (삭제되지 않은 일정만)
	List<Schedule> findByUser_UserIdxAndIsDeletedFalse(Long userIdx);

	// 특정 유저의 날짜 범위 내 일정 조회 (달력 표시용, 삭제되지 않은 일정)
	List<Schedule> findByUser_UserIdxAndStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndIsDeletedFalse(Long userIdx,
			LocalDateTime end, LocalDateTime start);

	// 제목에 특정 키워드가 포함된 일정 검색 (삭제되지 않은 일정)
	List<Schedule> findByUser_UserIdxAndTitleContainingIgnoreCaseAndIsDeletedFalse(Long userIdx, String title);

	// 일정 겹침 조회 (예: 시간 중복 체크)
	List<Schedule> findByUser_UserIdxAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(Long userIdx,
			LocalDateTime end, LocalDateTime start);

	@Query("SELECT s FROM Schedule s " + "WHERE s.user.userIdx = :userIdx "
			+ "AND (s.isDeleted = false OR s.isDeleted IS NULL) "
			+ "AND (LOWER(s.title) LIKE LOWER(CONCAT('%', :kw, '%')) "
			+ "     OR LOWER(s.description) LIKE LOWER(CONCAT('%', :kw, '%')))")
	List<Schedule> searchByTitleOrDescription(Long userIdx, String kw);

	// 일정 일괄 삭제
	List<Schedule> findByUser_UserIdxAndCreatedAtBetweenAndIsDeletedFalse(Long userIdx, LocalDateTime from,
			LocalDateTime to);

	List<Schedule> findByUser_UserIdxAndIsDeletedTrueOrderByUpdatedAtDesc(Long userIdx);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Schedule s " + "SET s.isDeleted = true, s.updatedAt = :now " + "WHERE s.scheduleId IN :ids "
			+ "AND (s.isDeleted = false OR s.isDeleted IS NULL)")
	int softDeleteByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Schedule s " + "SET s.isDeleted = true, s.updatedAt = :now " + "WHERE s.user.userIdx = :userIdx "
			+ "AND s.createdAt BETWEEN :from AND :to " + "AND (s.isDeleted = false OR s.isDeleted IS NULL)")
	int softDeleteRecent(@Param("userIdx") Long userIdx, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.user WHERE s.scheduleId = :scheduleId")
    Optional<Schedule> findByIdWithUser(@Param("scheduleId") Long scheduleId);
    
	/**
     * 이메일 알림이 활성화되었고 아직 발송되지 않은 일정 조회
     */
    List<Schedule> findByEmailNotificationEnabledTrueAndEmailNotificationSentFalse();
    
    /**
     * 특정 시간 범위에 해당하는 미발송 알림 일정 조회
     */
    List<Schedule> findByStartTimeBetweenAndEmailNotificationEnabledTrueAndEmailNotificationSentFalse(
        LocalDateTime start, 
        LocalDateTime end
    );

 
    // 다가오는 일정 조회 (알림용)
    @Query("SELECT s FROM Schedule s JOIN FETCH s.user " +
           "WHERE s.startTime BETWEEN :startTime AND :endTime " +
           "AND s.emailNotificationSent = false")
    List<Schedule> findUpcomingSchedulesForNotification(
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime
    );
}
