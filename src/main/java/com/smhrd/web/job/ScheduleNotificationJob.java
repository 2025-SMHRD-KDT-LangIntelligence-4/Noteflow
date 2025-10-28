package com.smhrd.web.job;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleRepository;
import com.smhrd.web.service.EmailService;
import com.smhrd.web.service.WebNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 일정 알림을 위한 Quartz Job
 * 특정 일정의 알림 시간이 되면 실행됨
 */
@Slf4j
@Component
public class ScheduleNotificationJob implements Job {
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private ScheduleRepository scheduleRepository;
    
    @Autowired(required = false)  // WebNotificationService가 없을 수도 있으니
    private WebNotificationService webNotificationService;
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("🔔 일정 알림 Job 실행 시작");
        
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long scheduleId = dataMap.getLong("scheduleId");
        
        try {
            // 일정 정보 조회 (User 정보도 함께)
            Optional<Schedule> scheduleOpt = scheduleRepository.findByIdWithUser(scheduleId);
            if (scheduleOpt.isEmpty()) {
                log.warn("⚠️ 해당 일정을 찾을 수 없습니다. Schedule ID: {}", scheduleId);
                return;
            }
            
            Schedule schedule = scheduleOpt.get();
            User user = schedule.getUser();
            
            // 이미 이메일 알림을 보낸 경우 스킵
            if (Boolean.TRUE.equals(schedule.getEmailNotificationSent())) {
                log.info("📧 이미 이메일 알림을 보낸 일정입니다. Schedule ID: {}", scheduleId);
                return;
            }
            
            // 1. 이메일 알림 발송
            try {
                emailService.sendScheduleNotificationEmail(
                    user.getEmail(), 
                    user.getNickname(), 
                    schedule
                );
                log.info("📧 이메일 알림 발송 완료: {} -> {}", schedule.getTitle(), user.getEmail());
            } catch (Exception e) {
                log.error("📧 이메일 알림 발송 실패: {}", e.getMessage(), e);
            }
            
            // 2. 웹 알림 저장 (브라우저 알림용)
            if (webNotificationService != null) {
                try {
                    webNotificationService.sendScheduleNotification(
                        user.getUserIdx(),
                        schedule.getTitle(),
                        schedule.getDescription(),
                        schedule.getStartTime()
                    );
                    log.info("🔔 웹 알림 저장 완료: {}", schedule.getTitle());
                } catch (Exception e) {
                    log.error("🔔 웹 알림 저장 실패: {}", e.getMessage(), e);
                }
            } else {
                log.warn("⚠️ WebNotificationService를 찾을 수 없어 웹 알림을 보낼 수 없습니다.");
            }
            
            // 3. 알림 발송 완료 플래그 업데이트
            schedule.setEmailNotificationSent(true);
            scheduleRepository.save(schedule);
            
            log.info("✅ 일정 알림 처리 완료. Email: {}, Schedule: {}", 
                     user.getEmail(), schedule.getTitle());
            
        } catch (Exception e) {
            log.error("❌ 일정 알림 처리 실패. Schedule ID: {}, Error: {}", 
                      scheduleId, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
    
    /**
     * 다가오는 모든 일정에 대해 알림 처리 (스케줄러용)
     * 매분 실행되어 알림이 필요한 일정들을 확인
     */
    public void processUpcomingSchedules() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime checkTime = now.plusMinutes(5); // 5분 후 일정들 확인
            
            // 다가오는 일정 조회 (아직 알림을 보내지 않은 것들)
            List<Schedule> upcomingSchedules = scheduleRepository.findUpcomingSchedulesForNotification(
                now, checkTime
            );
            
            log.info("⏰ 다가오는 일정 확인: {}개", upcomingSchedules.size());
            
            for (Schedule schedule : upcomingSchedules) {
                try {
                    User user = schedule.getUser();
                    
                    // 웹 알림 저장
                    if (webNotificationService != null) {
                        webNotificationService.sendScheduleNotification(
                            user.getUserIdx(),
                            schedule.getTitle(),
                            schedule.getDescription(),
                            schedule.getStartTime()
                        );
                    }
                    
                    // 이메일 알림 발송
                    if (schedule.isEmailNotificationEnabled()) {
                        emailService.sendScheduleNotificationEmail(
                            user.getEmail(),
                            user.getNickname(),
                            schedule
                        );
                    }
                    
                    // 알림 발송 완료 플래그 업데이트
                    schedule.setEmailNotificationSent(true);
                    scheduleRepository.save(schedule);
                    
                    log.info("📅 일정 알림 완료: {} (사용자: {})", 
                             schedule.getTitle(), user.getNickname());
                    
                } catch (Exception e) {
                    log.error("📅 개별 일정 알림 실패: {} (ID: {}), 오류: {}", 
                              schedule.getTitle(), schedule.getId(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("⚠️ 다가오는 일정 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}