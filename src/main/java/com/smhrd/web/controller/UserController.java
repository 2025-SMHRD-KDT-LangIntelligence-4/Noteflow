package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
                             @RequestParam(value = "interest_area", required = false) String interestArea,   // [추가]
                             @RequestParam(value = "learning_area", required = false) String learningArea,   // [추가]
                             @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                             @RequestParam(value = "deleteProfileImage", required = false) Boolean deleteProfileImage, // [추가]
                             RedirectAttributes redirectAttributes) { // [추가]

        String userId = authentication != null ? authentication.getName() : null;

        if (userId != null) {
            try {
                userService.updateUserInfo(userId, nickname, userEmail, userPw,
                        interestArea, learningArea, profileImage, deleteProfileImage); // [수정: 인자 확장]
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