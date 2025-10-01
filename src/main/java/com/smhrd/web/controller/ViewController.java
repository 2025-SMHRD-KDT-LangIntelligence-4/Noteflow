package com.smhrd.web.controller;

import com.smhrd.web.service.CustomUserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ViewController {

    private final CustomUserDetailsService userService;

    public ViewController(CustomUserDetailsService userService) {
        this.userService = userService;
    }

    @GetMapping({"/", "/main"})
    public String mainPage() {
        // feeds 모델 추가 예시
        // model.addAttribute("feeds", feedService.getAll());
        return "Main";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/mypage")
    public String myPage() {
    	
    	
    	
        return "MyPage";
    }

    @GetMapping("/mypage/edit")
    public String editMyPage() {
        return "editMypage";
    }

    @GetMapping("/schedule")
    public String scheduleManager() {
        return "schedule-manager";
    }

    @GetMapping("/notion/create")
    public String notionCreate() {
        return "NotionCreate";
    }

    @GetMapping("/notion/manage")
    public String notionManager() {
        return "NotionManager";
    }

    @GetMapping("/notion/complete")
    public String notionComplete() {
        return "notion_complete";
    }

    @GetMapping("/quiz/create")
    public String quizCreate() {
        return "quizCerate";
    }

    @GetMapping("/quiz/result")
    public String quizResult() {
        return "quizResult";
    }

    @PostMapping("/signup")
    public String signup(
            @RequestParam("user_id") String username,
            @RequestParam("user_pw") String rawPassword,
            @RequestParam("email") String email,
            @RequestParam(value = "mailing_agreed", defaultValue = "false") boolean mailingAgreed
    ) {
        userService.register(username, rawPassword, email, mailingAgreed);
        return "redirect:/login";
    }

    // 추가 API 필요 시 @PostMapping 구현...
}
