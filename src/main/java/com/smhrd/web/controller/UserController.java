package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        return "signup";  // signup.html
    }

    // --------------------------
    // 회원가입 처리
    // --------------------------
    @PostMapping("/signup")
    public String signup(@ModelAttribute User user) {
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
    @GetMapping("/mypage")
    public String mypage(Authentication authentication, Model model) {
        String userId = authentication.getName(); // 로그인한 유저 ID
        userService.getUserInfo(userId).ifPresent(user -> model.addAttribute("user", user));
        return "MyPage"; // MyPage.html
    }
}
