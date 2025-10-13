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
        model.addAttribute("activeMenu", "main");     // 활성화 메뉴 전달
        // feeds 모델 추가 예시
        // model.addAttribute("feeds", feedService.getAll());
        return "Main";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "로그인"); 
        model.addAttribute("activeMenu", "login");
        return "login";
    }

    @GetMapping("/mypage/edit")
    public String editMyPage(Model model) {
        model.addAttribute("pageTitle", "회원정보 수정"); 
        model.addAttribute("activeMenu", "mypage");
        return "editMypage";
    }

    @GetMapping("/schedule")
    public String scheduleManager(Model model) {
        model.addAttribute("pageTitle", "일정관리"); 
        model.addAttribute("activeMenu", "schedule");
        return "schedule-manager";
    }

//    @GetMapping("/notion/create")
//    public String notionCreate(Model model) {
//        model.addAttribute("pageTitle", "노션작성");
//        model.addAttribute("activeMenu", "notionCreate");
//        return "NotionCreate";
//    }

    @GetMapping("/notion/manage")
    public String notionManager(Model model) {
        model.addAttribute("pageTitle", "노션관리"); 
        model.addAttribute("activeMenu", "notionManage");
        return "NotionManager";
    }

    @GetMapping("/notion/complete")
    public String notionComplete(Model model) {
        model.addAttribute("pageTitle", "노션완료"); 
        model.addAttribute("activeMenu", "notionComplete");
        return "notion_complete";
    }

    @GetMapping("/quiz/create")
    public String quizCreate(Model model) {
        model.addAttribute("pageTitle", "문제생성"); 
        model.addAttribute("activeMenu", "quizCreate");
        return "quizCerate";
    }

    @GetMapping("/quiz/result")
    public String quizResult(Model model) {
        model.addAttribute("pageTitle", "문제결과"); 
        model.addAttribute("activeMenu", "quizResult");
        return "quizResult";
    }
    @GetMapping("/file-test")
    public String fileTest() {
        return "file-test";  // templates/file-test.html 렌더링
    }

    // 추가 API 필요 시 @PostMapping 구현...
}
