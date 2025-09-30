package com.smhrd.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class MainController {
	
	@GetMapping("/main")
    public String mainPage() {
        System.out.println(">>> Main Page 요청 처리됨: /main");
        return "main";
    }
	
	@GetMapping("/")
    public String rootPage() {
        System.out.println(">>> Root Page 요청 처리됨: /api/");
        // 루트 요청이 들어오면 main 페이지로 포워딩합니다.
        return "main";
    }
}
