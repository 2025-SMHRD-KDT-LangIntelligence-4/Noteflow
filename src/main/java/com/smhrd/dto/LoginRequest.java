package com.smhrd.dto;

import jakarta.validation.constraints.*;  // [추가] Validation 라이브러리
import lombok.*;

@Getter @Setter
public class LoginRequest {
    @NotBlank(message = "사용자명은 필수 입력입니다.") // [Validation]
    private String username;

    @NotBlank(message = "비밀번호는 필수 입력입니다.") // [Validation]
    private String password;
}