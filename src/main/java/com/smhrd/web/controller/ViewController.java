package com.smhrd.web.controller;

import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.CustomUserDetailsService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    private final CustomUserDetailsService userService;

    public ViewController(CustomUserDetailsService userService) {
        this.userService = userService;
    }

    @GetMapping({"/", "/main"})
    public String mainPage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "메인화면"); // 페이지 타이틀 전달
        model.addAttribute("activeMenu", "main");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        // 활성화 메뉴 전달
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
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "market";
    }

    @GetMapping("/mypage/edit")
    public String editMyPage(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "회원정보 수정"); 
        model.addAttribute("activeMenu", "mypage");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "editMypage";
    }

    @GetMapping("/schedule")
    public String scheduleManager(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "일정관리"); 
        model.addAttribute("activeMenu", "schedule");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "schedule-manager";
    }

//    @GetMapping("/notion/create")
//    public String notionCreate(Model model) {
//        model.addAttribute("pageTitle", "노션작성");
//        model.addAttribute("activeMenu", "notionCreate");
//        return "NotionCreate";
//    }

    @GetMapping("/notion/manage")
    public String notionManager(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "노션관리"); 
        model.addAttribute("activeMenu", "notionManage");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "NotionManager";
    }

    @GetMapping("/quiz/create")
    public String quizCreate(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제생성"); 
        model.addAttribute("activeMenu", "quizCreate");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
        }
        return "quizCreate";
    }
    @GetMapping("/quiz/test")
    public String quizTest(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제풀기"); 
        model.addAttribute("activeMenu", "quizTest");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
        }
        return "quizTest";
    }

    @GetMapping("/quiz/result")
    public String quizResult(Model model,@AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "문제결과"); 
        model.addAttribute("activeMenu", "quizResult");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
            String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        return "quizResult";
    }
    @GetMapping("/file-test")
    public String fileTest() {
        return "file-test";  // templates/file-test.html 렌더링
    }

    // 추가 API 필요 시 @PostMapping 구현...
   // 임시용
    @GetMapping("/lecture")
    public String lecturePage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("pageTitle", "강의목록"); 
        model.addAttribute("activeMenu", "recomLecture");
        if (userDetails != null) {
            // userDetails에서 닉네임 가져오기 (예: CustomUserDetails 사용)
        	String nickname = ((CustomUserDetails) userDetails).getNickname();
            model.addAttribute("nickname", nickname);
            String email = ((CustomUserDetails) userDetails).getEmail();
            model.addAttribute("email", email);
        }
        // templates/recomLecture.html
        return "recomLecture";
    }

    // 기존 링크 호환 (있다면): 더러운 주소로 보이지 않도록 /lecture 로 리다이렉트
    @GetMapping("/recomLecture")
    public String alias() {
        return "redirect:/lecture";
    }

}

