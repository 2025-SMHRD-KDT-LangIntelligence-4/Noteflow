package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.UserService;
import com.smhrd.web.service.EmailService; // ✅ 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final EmailService emailService; // ✅ 추가

    // --------------------------
    // 회원가입 폼
    // --------------------------
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("pageTitle", "회원가입");
        model.addAttribute("user", new User());
        return "signup";
    }

    // --------------------------
    // 회원가입 처리 (이메일 인증 안내 추가) ✅
    // --------------------------
    @PostMapping("/signup")
    public String signup(@ModelAttribute User user,
                         @RequestParam("userPwConfirm") String userPwConfirm) {

        if (!user.getUserPw().equals(userPwConfirm)) {
            return "redirect:/signup?error=pwMismatch";
        }

        try {
            userService.signup(user);
            // ✅ 이메일 인증 안내 메시지로 변경
            return "redirect:/login?signupSuccess=true&needEmailVerification=true";
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("이메일")) {
                return "redirect:/signup?error=emailDuplicate";
            }
            return "redirect:/signup?error=duplicate";
        }
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
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam("email") String email) {
        try {
            boolean available = !userService.isEmailDuplicate(email);
            Map<String, Boolean> result = new HashMap<>();
            result.put("available", available);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 로그 출력 후 기본값 반환
            System.err.println("이메일 중복 체크 오류: " + e.getMessage());
            Map<String, Boolean> result = new HashMap<>();
            result.put("available", false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    // --------------------------
    // 닉네임 중복 체크 (AJAX)
    // --------------------------
    @GetMapping("/check-nickname")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam("nickname") String nickname) {
        try {
            boolean available = !userService.isNickNameDuplicate(nickname);
            Map<String, Boolean> result = new HashMap<>();
            result.put("available", available);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Boolean> result = new HashMap<>();
            result.put("available", false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    // --------------------------
    // 현재 비밀번호 확인 (AJAX)
    // --------------------------
    @PostMapping("/verify-password")
    @ResponseBody
    public Map<String, Boolean> verifyPassword(Authentication authentication,
                                               @RequestParam("currentPw") String currentPw) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        boolean valid = userService.verifyPassword(userIdx, currentPw);

        Map<String, Boolean> result = new HashMap<>();
        result.put("valid", valid);
        return result;
    }

    // --------------------------
    // 계정 삭제 요청 (이메일 발송) ✅ 수정
    // --------------------------
    @PostMapping("/request-account-deletion")
    @ResponseBody
    public ResponseEntity<Map<String, String>> requestAccountDeletion(Authentication authentication) {
        
        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        Map<String, String> response = new HashMap<>();
        
        try {
            User user = userService.getUserInfo(userIdx)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            
            // 계정 삭제 확인 이메일 발송
            emailService.sendAccountDeletionEmail(user.getEmail(), user.getNickname());
            
            response.put("status", "success");
            response.put("message", "계정 삭제 확인 이메일을 발송했습니다. 메일함을 확인해주세요.");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "이메일 발송 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ✅ 기존 즉시 삭제 메서드는 주석 처리 (또는 삭제)
    /*
    @PostMapping("/delete-account")
    @ResponseBody
    public ResponseEntity<Void> deleteAccount(Authentication authentication) {
        // 이메일 인증 방식으로 대체됨
    }
    */

    // --------------------------
    // 마이페이지 (기존 코드 유지)
    // --------------------------
    @GetMapping("/mypage")
    public String mypageGet(Authentication authentication, Model model, @AuthenticationPrincipal UserDetails userDetails) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        userService.getUserInfo(userIdx)
                .ifPresent(user -> model.addAttribute("user", user));
        if (userDetails != null) {
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "MyPage";
    }

    @PostMapping("/mypage")
    public String mypagePost(Authentication authentication, Model model) {
        return mypageGet(authentication, model, null);
    }

    // --------------------------
    // 마이페이지 수정 폼 (기존 코드 유지)
    // --------------------------
    @GetMapping("/editMypage")
    public String editMypage(Authentication authentication, Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();
        User user = userService.getUserInfo(userIdx).orElse(new User());

        model.addAttribute("user", user);
        if (userDetails != null) {
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "editMypage";
    }

    // --------------------------
    // 마이페이지 수정 처리 (기존 코드 유지)
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

        Long userIdx = ((CustomUserDetails) authentication.getPrincipal()).getUserIdx();

        try {
            userService.updateUserInfo(userIdx, nickname, userEmail, userPw,
                    interestArea, learningArea, profileImage, deleteProfileImage);
            redirectAttributes.addFlashAttribute("message", "회원 정보가 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "회원 정보 수정 중 오류가 발생했습니다.");
        }

        return "redirect:/mypage";
    }
}
