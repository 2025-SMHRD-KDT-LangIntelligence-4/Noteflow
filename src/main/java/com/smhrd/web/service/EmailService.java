package com.smhrd.web.service;

import com.smhrd.web.entity.EmailVerificationToken;
import com.smhrd.web.entity.EmailVerificationToken.TokenType;
import com.smhrd.web.entity.Schedule;
import com.smhrd.web.repository.EmailVerificationTokenRepository;
import com.smhrd.web.entity.Schedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


import java.time.format.DateTimeFormatter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationTokenRepository tokenRepository;
    private final TemplateEngine templateEngine; // ✅ Thymeleaf 템플릿 엔진 추가

    // ✅ 환경별 설정값들
    @Value("${app.base-url:http://localhost}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@ssaegim.com}")
    private String fromEmail;

    /**
     * 회원가입 인증 메일 발송 (24시간 유효)
     */
    public void sendSignupVerificationEmail(String email, String nickname) {
        try {
            String token = generateVerificationToken(email, TokenType.SIGNUP_VERIFICATION, 24);
            String verificationLink = baseUrl + "/auth/verify-email?token=" + token;

            // ✅ Thymeleaf 템플릿 사용
            Context context = new Context();
            context.setVariable("nickname", nickname);
            context.setVariable("verificationLink", verificationLink);
            context.setVariable("baseUrl", baseUrl);
            
            String htmlContent = templateEngine.process("email/signup-verification", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "새김(SSAEGIM)");
            helper.setTo(email);
            helper.setSubject("📚 새김(SSAEGIM) 이메일 인증");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("회원가입 인증 메일 발송 성공: {}", email);

        } catch (Exception e) {
            log.error("회원가입 인증 메일 발송 실패: {} - {}", email, e.getMessage());
            throw new RuntimeException("이메일 발송 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 비밀번호 재설정 메일 발송 (1시간 유효)
     */
    public void sendPasswordResetEmail(String email, String nickname) {
        try {
            String token = generateVerificationToken(email, TokenType.PASSWORD_RESET, 1);
            String resetLink = baseUrl + "/auth/reset-password?token=" + token;

            // ✅ Thymeleaf 템플릿 사용
            Context context = new Context();
            context.setVariable("nickname", nickname);
            context.setVariable("resetLink", resetLink);
            context.setVariable("baseUrl", baseUrl);
            
            String htmlContent = templateEngine.process("email/password-reset", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "새김(SSAEGIM)");
            helper.setTo(email);
            helper.setSubject("🔑 새김(SSAEGIM) 비밀번호 재설정");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("비밀번호 재설정 메일 발송 성공: {}", email);

        } catch (Exception e) {
            log.error("비밀번호 재설정 메일 발송 실패: {} - {}", email, e.getMessage());
            throw new RuntimeException("이메일 발송 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 회원탈퇴 확인 메일 발송 (24시간 유효)
     */
    public void sendAccountDeletionEmail(String email, String nickname) {
        try {
            String token = generateVerificationToken(email, TokenType.ACCOUNT_DELETION, 24);
            String deletionLink = baseUrl + "/auth/confirm-deletion?token=" + token;

            // ✅ Thymeleaf 템플릿 사용
            Context context = new Context();
            context.setVariable("nickname", nickname);
            context.setVariable("deletionLink", deletionLink);
            context.setVariable("baseUrl", baseUrl);
            
            String htmlContent = templateEngine.process("email/account-deletion", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "새김(SSAEGIM)");
            helper.setTo(email);
            helper.setSubject("⚠️ 새김(SSAEGIM) 회원탈퇴 확인");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("계정 삭제 확인 메일 발송 성공: {}", email);

        } catch (Exception e) {
            log.error("계정 삭제 확인 메일 발송 실패: {} - {}", email, e.getMessage());
            throw new RuntimeException("이메일 발송 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 토큰 생성
     */
    private String generateVerificationToken(String email, TokenType tokenType, int expiryHours) {
        // 기존 미사용 토큰 삭제 (있다면)
        tokenRepository.deleteByEmailAndTokenTypeAndUsed(email, tokenType, false);

        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(expiryHours);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
            .token(token)
            .email(email)
            .tokenType(tokenType)
            .expiryDate(expiryDate)
            .used(false)
            .build();

        tokenRepository.save(verificationToken);
        return token;
    }

    /**
     * 토큰 검증
     */
    public boolean verifyToken(String token, TokenType expectedType) {
        Optional<EmailVerificationToken> tokenOpt = tokenRepository.findByTokenAndTokenTypeAndUsed(token, expectedType, false);
        
        if (tokenOpt.isEmpty()) {
            log.warn("토큰을 찾을 수 없음: {} ({})", token, expectedType);
            return false;
        }

        EmailVerificationToken verificationToken = tokenOpt.get();
        
        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("토큰이 만료됨: {} ({})", token, expectedType);
            return false;
        }

        // 토큰 사용 처리
        verificationToken.setUsed(true);
        tokenRepository.save(verificationToken);
        
        log.info("토큰 검증 성공: {} ({})", token, expectedType);
        return true;
    }

    /**
     * 토큰으로부터 이메일 주소 추출
     */
    public String getEmailFromToken(String token, TokenType tokenType) {
        return tokenRepository.findByTokenAndTokenTypeAndUsed(token, tokenType, true)
            .map(EmailVerificationToken::getEmail)
            .orElse(null);
    }

    public void sendScheduleNotificationEmail(String email, String nickname, Schedule schedule) {
        try {
            // Thymeleaf Context 생성
            Context context = new Context();
            context.setVariable("nickname", nickname);
            context.setVariable("scheduleTitle", schedule.getTitle());
            context.setVariable("scheduleDescription", schedule.getDescription());
            
            // 날짜 포맷팅
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm");
            
            if (Boolean.TRUE.equals(schedule.getIsAllDay())) {
                context.setVariable("isAllDay", true);
                context.setVariable("startDate", schedule.getStartTime().format(dateFormatter));
                context.setVariable("endDate", schedule.getEndTime().format(dateFormatter));
            } else {
                context.setVariable("isAllDay", false);
                context.setVariable("startDateTime", schedule.getStartTime().format(datetimeFormatter));
                context.setVariable("endDateTime", schedule.getEndTime().format(datetimeFormatter));
            }
            
            context.setVariable("location", schedule.getLocation() != null ? schedule.getLocation() : "장소 미정");
            context.setVariable("minutesBefore", schedule.getNotificationMinutesBefore());
            context.setVariable("baseUrl", baseUrl);
            
            // 템플릿 처리
            String htmlContent = templateEngine.process("email/schedule-notification", context);
            
            // 이메일 메시지 생성
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, "새김(SSAEGIM)");
            helper.setTo(email);
            helper.setSubject("📅 [일정 알림] " + schedule.getTitle());
            helper.setText(htmlContent, true);
            
            // 이메일 발송
            mailSender.send(message);
            
            log.info("일정 알림 이메일 발송 성공: {} - {}", email, schedule.getTitle());
            
        } catch (Exception e) {
            log.error("일정 알림 이메일 발송 실패: {} - {}", email, e.getMessage(), e);
            throw new RuntimeException("이메일 발송 중 오류가 발생했습니다.", e);
        }
    }
}
