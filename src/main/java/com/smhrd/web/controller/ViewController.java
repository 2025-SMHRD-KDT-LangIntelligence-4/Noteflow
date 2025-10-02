package com.smhrd.web.controller;

import com.smhrd.web.service.CustomUserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String mainPage(Model model) {
        model.addAttribute("pageTitle", "메인화면"); // 페이지 타이틀 전달
        // feeds 모델 추가 예시
        // model.addAttribute("feeds", feedService.getAll());
        return "Main";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "로그인"); // 페이지 타이틀 전달
        return "login";
    }

    @GetMapping("/mypage/edit")
    public String editMyPage(Model model) {
        model.addAttribute("pageTitle", "회원정보 수정"); // 페이지 타이틀 전달
        return "editMypage";
    }

    @GetMapping("/schedule")
    public String scheduleManager(Model model) {
        model.addAttribute("pageTitle", "일정관리"); // 페이지 타이틀 전달
        return "schedule-manager";
    }

    @GetMapping("/notion/create")
    public String notionCreate(Model model) {
        model.addAttribute("pageTitle", "노션 작성"); // 페이지 타이틀 전달
        return "NotionCreate";
    }

    @GetMapping("/notion/manage")
    public String notionManager(Model model) {
        model.addAttribute("pageTitle", "노션 관리"); // 페이지 타이틀 전달
        return "NotionManager";
    }

    @GetMapping("/notion/complete")
    public String notionComplete(Model model) {
        model.addAttribute("pageTitle", "노션 완료"); // 페이지 타이틀 전달
        return "notion_complete";
    }

    @GetMapping("/quiz/create")
    public String quizCreate(Model model) {
        model.addAttribute("pageTitle", "문제 생성"); // 페이지 타이틀 전달
        return "quizCerate";
    }

    @GetMapping("/quiz/result")
    public String quizResult(Model model) {
        model.addAttribute("pageTitle", "문제 결과"); // 페이지 타이틀 전달
        return "quizResult";
    }

    // 추가 API 필요 시 @PostMapping 구현...
}
