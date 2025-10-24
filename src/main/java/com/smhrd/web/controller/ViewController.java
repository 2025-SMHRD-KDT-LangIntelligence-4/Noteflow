package com.smhrd.web.controller;

import com.smhrd.web.entity.User;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.UserService; // CustomUserDetailsService 대신 UserService 사용

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    private final UserService userService; // UserService로 변경

    public ViewController(UserService userService) { // 생성자 주입 변경
        this.userService = userService;
    }
    
    // 재사용 가능한 유저 정보 로딩 로직
    private void loadAndAddModelUserInfo(UserDetails userDetails, Model model) {
        if (userDetails instanceof CustomUserDetails) {
            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
            Long userIdx = customUserDetails.getUserIdx();

            // 1. UserService를 통해 User 엔티티를 조회하고 "user"라는 이름으로 모델에 추가
            userService.getUserInfo(userIdx)
                .ifPresent(user -> {
                    model.addAttribute("user", user);
                    
                    // 2. 기존의 nickname과 email도 user 엔티티에서 가져와 추가 (header.html 호환성 유지)
                    model.addAttribute("nickname", user.getNickname()); 
                    model.addAttribute("email", user.getEmail());
                });
        }
    }


    @GetMapping({"/", "/main"})
    public String mainPage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "메인화면"); // 페이지 타이틀 전달
        model.addAttribute("activeMenu", "main");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
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
    
    @GetMapping("/market")
    public String marketPage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "마켓"); 
        model.addAttribute("activeMenu", "market");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "market";
    }

    @GetMapping("/mypage/edit")
    public String editMyPage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "회원정보 수정"); 
        model.addAttribute("activeMenu", "mypage");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "editMypage";
    }

    @GetMapping("/schedule")
    public String scheduleManager(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "일정관리"); 
        model.addAttribute("activeMenu", "schedule");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "schedule-manager";
    }

    @GetMapping("/notion/manage")
    public String notionManager(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "노션관리"); 
        model.addAttribute("activeMenu", "notionManage");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "NotionManager";
    }

    @GetMapping("/quiz/create")
    public String quizCreate(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제생성"); 
        model.addAttribute("activeMenu", "quizCreate");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "quizCreate";
    }
    
    @GetMapping("/quiz/test")
    public String quizTest(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제풀기"); 
        model.addAttribute("activeMenu", "quizTest");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "quizTest";
    }

    @GetMapping("/quiz/result")
    public String quizResult(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제결과"); 
        model.addAttribute("activeMenu", "quizResult");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        return "quizResult";
    }
    
    @GetMapping("/file-test")
    public String fileTest() {
        return "file-test";  // templates/file-test.html 렌더링
    }

    // 임시용
    @GetMapping("/lecture")
    public String lecturePage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "강의목록"); 
        model.addAttribute("activeMenu", "lecture");
        
        loadAndAddModelUserInfo(userDetails, model); // 유저 정보 로드 및 모델 추가
        
        // templates/recomLecture.html
        return "recomLecture";
    }

    // 기존 링크 호환 (있다면): 더러운 주소로 보이지 않도록 /lecture 로 리다이렉트
    @GetMapping("/recomLecture")
    public String alias() {
        return "redirect:/lecture";
    }

}
