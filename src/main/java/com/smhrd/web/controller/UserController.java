package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // --------------------------
    // 회원가입 폼
    // --------------------------
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("pageTitle", "회원가입");
        model.addAttribute("user", new User());
        return "signup"; // signup.html
    }

    // --------------------------
    // 회원가입 처리
    // --------------------------
    @PostMapping("/signup")
    public String signup(@ModelAttribute User user,
                         @RequestParam("userPwConfirm") String userPwConfirm) {

        if (!user.getUserPw().equals(userPwConfirm)) {
            return "redirect:/signup?error=pwMismatch";
        }

        try {
            userService.signup(user);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("이메일")) {
                return "redirect:/signup?error=emailDuplicate";
            }
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
    // 이메일 중복 체크 (AJAX)
    // --------------------------
    @GetMapping("/check-email")
    @ResponseBody
    public Map<String, Boolean> checkEmail(@RequestParam("email") String email) {
        boolean available = !userService.isEmailDuplicate(email);
        Map<String, Boolean> result = new HashMap<>();
        result.put("available", available);
        return result;
    }

    // --------------------------
    // 현재 비밀번호 확인 (AJAX)
    // --------------------------
    @PostMapping("/verify-password")
    @ResponseBody
    public Map<String, Boolean> verifyPassword(Authentication authentication,
                                               @RequestParam("currentPw") String currentPw) {
        String userId = authentication.getName();
        boolean valid = userService.verifyPassword(userId, currentPw);
        Map<String, Boolean> result = new HashMap<>();
        result.put("valid", valid);
        return result;
    }

    // --------------------------
    // 계정 삭제 (AJAX)
    // --------------------------
    @PostMapping("/delete-account")
    @ResponseBody
    public ResponseEntity<Void> deleteAccount(Authentication authentication) {
        String userId = authentication.getName();
        try {
            userService.deleteUserAccount(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --------------------------
    // 마이페이지
    // --------------------------
    @GetMapping("/mypage")
    public String mypageGet(Authentication authentication, Model model) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId != null) {
            userService.getUserInfo(userId)
                       .ifPresent(user -> model.addAttribute("user", user));
        } else {
            model.addAttribute("user", new User());
        }
        return "MyPage";
    }

    @PostMapping("/mypage")
    public String mypagePost(Authentication authentication, Model model) {
        return mypageGet(authentication, model);
    }

    // --------------------------
    // 마이페이지 수정 폼
    // --------------------------
    @GetMapping("/editMypage")
    public String editMypage(Authentication authentication, Model model) {
        String userId = authentication != null ? authentication.getName() : null;
        System.out.println("Authentication: " + authentication);
        System.out.println("userId from auth: " + userId);
        User user = new User(); // 기본 객체 생성
        if (userId != null) {
            user = userService.getUserInfo(userId).orElse(user); // DB에서 가져오거나 기본 객체
        }
        
        model.addAttribute("user", user); // 항상 null-safe 보장
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
                             @RequestParam(value = "interest_area", required = false) String interestArea,
                             @RequestParam(value = "learning_area", required = false) String learningArea,
                             @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                             @RequestParam(value = "deleteProfileImage", required = false) Boolean deleteProfileImage,
                             RedirectAttributes redirectAttributes) {

        String userId = authentication != null ? authentication.getName() : null;

        if (userId != null) {
            try {
                userService.updateUserInfo(userId, nickname, userEmail, userPw,
                        interestArea, learningArea, profileImage, deleteProfileImage);
                redirectAttributes.addFlashAttribute("message", "회원 정보가 성공적으로 수정되었습니다.");
            } catch (Exception e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("error", "회원 정보 수정 중 오류가 발생했습니다.");
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "로그인 정보가 유효하지 않습니다.");
        }

        return "redirect:/mypage";
    }
}
