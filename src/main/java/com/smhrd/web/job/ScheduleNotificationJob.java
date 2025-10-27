package com.smhrd.web.job;

import com.smhrd.web.entity.Schedule;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ScheduleRepository;
import com.smhrd.web.service.EmailService;
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
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("일정 알림 이메일 Job 실행 시작");
        
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long scheduleId = dataMap.getLong("scheduleId");
        
        try {
        	
        	 // ✅ User 정보를 함께 가져오기
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
            
            // 이메일 발송
            emailService.sendScheduleNotificationEmail(
                user.getEmail(),
                user.getNickname(),
                schedule
            );
            
            // 발송 완료 플래그 업데이트
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
