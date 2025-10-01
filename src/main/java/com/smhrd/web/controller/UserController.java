package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    // 회원가입 폼 (GET /signup)
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("user", new User());
        return "signup";  // signup.html
    }

    // 회원가입 처리 (POST /signup)
    @PostMapping("/signup")
    public String signup(@ModelAttribute User user) {
        // 중복 체크 (예: 아이디 중복 방지)
        if (userRepo.findByUserId(user.getUserId()).isPresent()) {
            // 이미 존재하면 다시 signup 페이지로
            return "redirect:/signup?error=duplicate";
        }

        // 비밀번호 암호화
        user.setUserPw(passwordEncoder.encode(user.getUserPw()));

        // 기본값 세팅
        user.setUserRole("USER");                // 기본 권한 USER
        user.setCreatedAt(LocalDateTime.now());  // 가입일
        user.setLastLogin(null);                 // 로그인 기록 없음
        if (user.getAttachmentCount() == null) {
            user.setAttachmentCount(0);
        }
        if (user.getMailingAgreed() == null) {
            user.setMailingAgreed(false);
        }
        
        // DB 저장
        userRepo.save(user);

        // 회원가입 완료 후 로그인 페이지로 이동
        return "redirect:/login?signupSuccess";
    }
    // 아이디 중복 체크 API (AJAX 요청)
    @GetMapping("/checkId")
    public boolean checkId(@RequestParam("userId") String userId) {
        // 존재 여부 확인 (true → 중복, false → 사용 가능)
        return userRepo.findByUserId(userId).isPresent();
    }
}
