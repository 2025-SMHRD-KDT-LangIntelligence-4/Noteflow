package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // --------------------------
    // 회원가입 폼
    // --------------------------
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("user", new User());
        return "signup"; // signup.html
    }

    // --------------------------
    // 회원가입 처리
    // --------------------------
    @PostMapping("/signup")
    public String signup(@ModelAttribute User user,
                         @RequestParam("userPwConfirm") String userPwConfirm) {
        // 비밀번호 확인 검증
        if (!user.getUserPw().equals(userPwConfirm)) {
            return "redirect:/signup?error=pwMismatch";
        }

        try {
            userService.signup(user);
        } catch (IllegalArgumentException e) {
            return "redirect:/signup?error=duplicate";
        }
        return "redirect:/login?signupSuccess";
    }

    // --------------------------
    // 아이디 중복 체크 (AJAX)
    // --------------------------
    @GetMapping("/checkId")
    @ResponseBody
    public boolean checkId(@RequestParam("userId") String userId) {
        return userService.isUserIdDuplicate(userId);
    }

    // --------------------------
    // 마이페이지
    // --------------------------
    // GET 요청 허용
    @GetMapping("/mypage")
    public String mypageGet(Authentication authentication, Model model) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId != null) {
            userService.getUserInfo(userId)
                       .ifPresent(user -> model.addAttribute("user", user));
        } else {
            model.addAttribute("user", new User()); // null-safe 처리
        }
        return "MyPage"; // MyPage.html
    }

    // POST 요청 (보안성을 위해 form에서 사용)
    @PostMapping("/mypage")
    public String mypagePost(Authentication authentication, Model model) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId != null) {
            userService.getUserInfo(userId)
                       .ifPresent(user -> model.addAttribute("user", user));
        } else {
            model.addAttribute("user", new User()); // null-safe 처리
        }
        return "MyPage"; // MyPage.html
    }

    // --------------------------
    // 마이페이지 수정 폼
    // --------------------------
    @GetMapping("/editMypage")
    public String editMypage(Authentication authentication, Model model) {
        String userId = authentication != null ? authentication.getName() : null;

        if (userId != null) {
            userService.getUserInfo(userId).ifPresentOrElse(
                user -> model.addAttribute("user", user),
                () -> model.addAttribute("user", new User()) // null-safe 기본 객체
            );
        } else {
            model.addAttribute("user", new User()); // 인증 정보 없을 경우 대비
        }

        return "editMypage";
    }

    // --------------------------
    // 마이페이지 수정 처리
    // --------------------------
    @PostMapping("/editMypage")
    public String editMypage(Authentication authentication,
                             @RequestParam(value = "nickname", required = false) String nickname,
                             @RequestParam(value = "userEmail", required = false) String userEmail,
                             @RequestParam(value = "userPw", required = false) String userPw,
                             @RequestParam(value = "profileImage", required = false) MultipartFile profileImage) {

        String userId = authentication != null ? authentication.getName() : null;

        if (userId != null) {
            userService.updateUserInfo(userId, nickname, userEmail, userPw, profileImage);
        }
        // userId null일 경우 처리 생략 (보안 상 무시)

        return "redirect:/mypage?editSuccess";
    }
}
