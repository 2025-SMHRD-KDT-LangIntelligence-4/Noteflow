package com.smhrd.controller;

import com.smhrd.dto.*;
import com.smhrd.service.UserService;
import jakarta.validation.Valid; // [추가] Validation 적용
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 회원가입 API
    @PostMapping("/signup")
    public String signup(@Valid @RequestBody SignupRequest request) { // [Validation 적용]
        userService.signup(request);
        return "회원가입 성공";
    }

    // 로그인 API
    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest request) { // [Validation 적용]
        return userService.login(request); // JWT 토큰 반환
    }

    // 내 정보 조회 API
    @GetMapping("/me")
    public UserResponse getUserInfo(@RequestHeader("Authorization") String token) {
        String username = token.replace("Bearer ", "");
        return userService.getUserInfo(username);
    }

    // 내 정보 수정 API
    @PutMapping("/me")
    public String updateUser(@RequestHeader("Authorization") String token,
                             @Valid @RequestBody SignupRequest request) { // [Validation 적용]
        String username = token.replace("Bearer ", "");
        userService.updateUser(username, request);
        return "회원정보 수정 완료";
    }
}