package com.smhrd.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class MainController {
	
	// 해당 페이지에서는 메인페이지와 관련된 이동만 다룰 것
	@GetMapping("/main")
    public String mainPage() {
        System.out.println(">>> Main Page 요청 처리됨: /main");
        return "main";
    }
	
	@GetMapping("/")
    public String rootPage() {
        System.out.println(">>> Root Page 요청 처리됨: /");
        return "main";
    }
}
