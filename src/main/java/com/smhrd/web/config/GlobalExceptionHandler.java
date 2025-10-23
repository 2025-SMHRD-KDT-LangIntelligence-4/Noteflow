package com.smhrd.web.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.NoHandlerFoundException;

@Controller
public class GlobalExceptionHandler implements ErrorController {

    /**
     * 404 에러 처리 - 메인 페이지로 리다이렉트
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute("jakarta.servlet.error.status_code");
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            
            // 404 에러면 메인으로
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "redirect:/main";
            }
            
            // 403 에러는 로그인으로
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "redirect:/login";
            }
        }
        
        // 기타 에러도 메인으로
        return "redirect:/main";
    }
}


