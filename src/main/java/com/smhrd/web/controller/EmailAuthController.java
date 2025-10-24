package com.smhrd.web.controller;

import com.smhrd.web.entity.EmailVerificationToken.TokenType;
import com.smhrd.web.entity.User;
import com.smhrd.web.service.EmailService;
import com.smhrd.web.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailService emailService;
    private final UserService userService;

    // 이메일 인증
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        if (emailService.verifyToken(token, TokenType.SIGNUP_VERIFICATION)) {
            String email = emailService.getEmailFromToken(token, TokenType.SIGNUP_VERIFICATION);
            userService.activateAccount(email);
            model.addAttribute("message", "이메일 인증이 완료되었습니다. 이제 로그인하실 수 있습니다.");
            return "auth/verification-success";
        } else {
            model.addAttribute("error", "유효하지 않거나 만료된 인증 링크입니다.");
            return "auth/verification-failed";
        }
    }

    // ✅ 비밀번호 찾기 요청 페이지
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "auth/forgot-password";
    }

    // ✅ 비밀번호 찾기 이메일 발송
    @PostMapping("/forgot-password")
    public String sendPasswordResetEmail(@RequestParam String email, Model model) {
        try {
            Optional<User> userOpt = userService.getUserByEmail(email);
            
            if (userOpt.isEmpty()) {
                model.addAttribute("error", "등록되지 않은 이메일 주소입니다.");
                return "auth/forgot-password";
            }

            User user = userOpt.get();
            emailService.sendPasswordResetEmail(user.getEmail(), user.getNickname());
            
            model.addAttribute("email", email);
            return "auth/forgot-password-sent";
            
        } catch (Exception e) {
            log.error("비밀번호 재설정 이메일 발송 실패: {}", e.getMessage());
            model.addAttribute("error", "이메일 발송 중 오류가 발생했습니다.");
            return "auth/forgot-password";
        }
    }

    // 비밀번호 재설정 폼
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        if (emailService.verifyToken(token, TokenType.PASSWORD_RESET)) {
            model.addAttribute("token", token);
            return "auth/reset-password-form";
        } else {
            model.addAttribute("error", "유효하지 않거나 만료된 링크입니다.");
            return "auth/verification-failed";
        }
    }

    // 비밀번호 재설정 처리
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                              @RequestParam String newPassword,
                              Model model) {
        String email = emailService.getEmailFromToken(token, TokenType.PASSWORD_RESET);
        if (email != null) {
            userService.resetPassword(email, newPassword);
            model.addAttribute("message", "비밀번호가 성공적으로 변경되었습니다.");
            return "auth/password-reset-success";
        } else {
            model.addAttribute("error", "비밀번호 재설정에 실패했습니다.");
            return "auth/verification-failed";
        }
    }

    // 계정 삭제 확인
    @GetMapping("/confirm-deletion")
    public String confirmAccountDeletion(@RequestParam String token, Model model) {
        if (emailService.verifyToken(token, TokenType.ACCOUNT_DELETION)) {
            String email = emailService.getEmailFromToken(token, TokenType.ACCOUNT_DELETION);
            userService.deleteAccountByEmail(email);
            model.addAttribute("message", "계정이 성공적으로 삭제되었습니다.");
            return "auth/account-deleted";
        } else {
            model.addAttribute("error", "유효하지 않거나 만료된 링크입니다.");
            return "auth/verification-failed";
        }
    }

    // 인증 이메일 재발송
    @PostMapping("/resend-verification")
    @ResponseBody
    public ResponseEntity<Map<String, String>> resendVerificationEmail(@RequestParam String email) {
        Map<String, String> response = new HashMap<>();
        try {
            Optional<User> userOpt = userService.getUserByEmail(email);
            if (userOpt.isEmpty()) {
                response.put("status", "error");
                response.put("message", "등록되지 않은 이메일입니다.");
                return ResponseEntity.badRequest().body(response);
            }

            User user = userOpt.get();
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                response.put("status", "error");
                response.put("message", "이미 인증된 계정입니다.");
                return ResponseEntity.badRequest().body(response);
            }

            emailService.sendSignupVerificationEmail(user.getEmail(), user.getNickname());
            response.put("status", "success");
            response.put("message", "인증 이메일이 재발송되었습니다.");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "이메일 발송 중 오류가 발생했습니다.");
            return ResponseEntity.status(500).body(response);
        }
    }
}
