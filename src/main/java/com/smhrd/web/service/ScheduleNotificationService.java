package com.smhrd.web.service;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.job.ScheduleNotificationJob;
import com.smhrd.web.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleNotificationService {
    
    private final Scheduler scheduler;
    private final ScheduleRepository scheduleRepository; 
    /**
     * 일정 알림 이메일 스케줄링
     * 
     * @param schedule 알림을 보낼 일정
     */
    public void scheduleNotificationEmail(Schedule schedule) {
        // 알림이 비활성화되어 있으면 스킵
        if (!Boolean.TRUE.equals(schedule.getEmailNotificationEnabled())) {
            log.debug("이메일 알림이 비활성화된 일정입니다. Schedule ID: {}", schedule.getScheduleId());
            return;
        }
        
        try {
            // 알림 발송 시간 계산 (일정 시작 시간 - 설정된 분)
            LocalDateTime notificationTime = schedule.getStartTime()
                .minusMinutes(schedule.getNotificationMinutesBefore());
            
            // 현재 시간보다 과거면 스케줄링하지 않음
            if (notificationTime.isBefore(LocalDateTime.now())) {
                log.warn("알림 시간이 과거입니다. 스케줄링하지 않습니다. Schedule: {}", schedule.getTitle());
                return;
            }
            
            // 고유한 Job ID 생성
            String jobId = "schedule-notification-" + schedule.getScheduleId() + "-" + UUID.randomUUID().toString();
            
            // JobDetail 생성
            JobDetail jobDetail = JobBuilder.newJob(ScheduleNotificationJob.class)
                .withIdentity(jobId, "schedule-notifications")
                .withDescription("일정 알림: " + schedule.getTitle())
                .usingJobData("scheduleId", schedule.getScheduleId())
                .storeDurably(false)
                .build();
            
            // Trigger 생성 (특정 시간에 한 번만 실행)
            Date triggerDate = Date.from(notificationTime.atZone(ZoneId.systemDefault()).toInstant());
            
            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "-trigger", "schedule-notifications")
                .forJob(jobDetail)
                .startAt(triggerDate)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                    .withMisfireHandlingInstructionFireNow())
                .build();
            
            // 스케줄러에 Job 등록
            scheduler.scheduleJob(jobDetail, trigger);
            
            // Schedule Entity에 Job ID 저장 (취소를 위해)
            schedule.setQuartzJobId(jobId);
            scheduleRepository.save(schedule);
            log.info("일정 알림 이메일 스케줄링 완료. Job ID: {}, 발송 시간: {}", jobId, notificationTime);
            
        } catch (SchedulerException e) {
            log.error("일정 알림 스케줄링 실패. Schedule ID: {}, Error: {}", 
                      schedule.getScheduleId(), e.getMessage(), e);
            throw new RuntimeException("일정 알림 스케줄링 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 일정 알림 스케줄 취소
     * 
     * @param schedule 취소할 일정
     */
    public void cancelNotificationEmail(Schedule schedule) {
        if (schedule.getQuartzJobId() == null) {
            log.debug("스케줄된 알림이 없습니다. Schedule ID: {}", schedule.getScheduleId());
            return;
        }
        
        try {
            JobKey jobKey = new JobKey(schedule.getQuartzJobId(), "schedule-notifications");
            
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("일정 알림 스케줄 취소 완료. Job ID: {}", schedule.getQuartzJobId());
            }
            
            schedule.setQuartzJobId(null);
            scheduleRepository.save(schedule);
        } catch (SchedulerException e) {
            log.error("일정 알림 취소 실패. Job ID: {}, Error: {}", 
                      schedule.getQuartzJobId(), e.getMessage(), e);
            throw new RuntimeException("일정 알림 취소 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 일정 알림 재스케줄링 (수정 시 사용)
     * 
     * @param schedule 재스케줄링할 일정
     */
    public void rescheduleNotificationEmail(Schedule schedule) {
        // 기존 스케줄 취소
        cancelNotificationEmail(schedule);
        
        // 새로운 스케줄 등록
        scheduleNotificationEmail(schedule);
    }
}
