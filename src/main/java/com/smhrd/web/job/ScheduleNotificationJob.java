package com.smhrd.web.job;

// 기존 import들은 그대로 유지
import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleRepository;
import com.smhrd.web.service.EmailService;
// ⭐ 새로운 import 단 1개만 추가
import com.smhrd.web.service.WebNotificationService; // ← 이것만 추가

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Optional;

/**
 * 일정 알림 이메일을 발송하는 Quartz Job
 */
@Slf4j
@Component
public class ScheduleNotificationJob implements Job {

    @Autowired
    private EmailService emailService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    // ⭐ 새로운 의존성 단 1개만 추가
    @Autowired(required = false) // required = false로 선택적 주입
    private WebNotificationService webNotificationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("일정 알림 이메일 Job 실행 시작");

        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long scheduleId = dataMap.getLong("scheduleId");

        try {
            // ✅ 기존 코드 그대로 유지
            Optional<Schedule> scheduleOpt = scheduleRepository.findByIdWithUser(scheduleId);
            if (scheduleOpt.isEmpty()) {
                log.warn("일정을 찾을 수 없습니다. Schedule ID: {}", scheduleId);
                return;
            }

            Schedule schedule = scheduleOpt.get();
            User user = schedule.getUser();

            // 이미 발송된 경우 스킵
            if (Boolean.TRUE.equals(schedule.getEmailNotificationSent())) {
                log.info("이미 알림이 발송된 일정입니다. Schedule ID: {}", scheduleId);
                return;
            }

            // ✅ 기존 이메일 발송 로직 그대로 유지
            emailService.sendScheduleNotificationEmail(
                user.getEmail(),
                user.getNickname(),
                schedule
            );

            // ⭐ 웹 알림 추가 (단 3줄만!)
            if (webNotificationService != null) {
                try {
                    webNotificationService.sendScheduleNotification(
                        user.getUserIdx(),
                        schedule.getTitle(),
                        schedule.getDescription(),
                        schedule.getStartTime()
                    );
                    log.info("✅ 웹 알림도 발송 완료: {}", schedule.getTitle());
                } catch (Exception e) {
                    log.error("❌ 웹 알림 발송 실패 (이메일은 성공): {}", e.getMessage());
                    // 웹 알림 실패해도 전체 Job은 성공으로 처리
                }
            }

            // ✅ 기존 코드 그대로 유지
            schedule.setEmailNotificationSent(true);
            scheduleRepository.save(schedule);

            log.info("일정 알림 이메일 발송 완료. Email: {}, Schedule: {}",
                    user.getEmail(), schedule.getTitle());

        } catch (Exception e) {
            log.error("일정 알림 이메일 발송 실패. Schedule ID: {}, Error: {}",
                    scheduleId, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}