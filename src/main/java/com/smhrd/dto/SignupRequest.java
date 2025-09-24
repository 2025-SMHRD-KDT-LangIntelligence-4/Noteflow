package com.smhrd.dto;

import jakarta.validation.constraints.*;  // [추가] Validation 라이브러리
import lombok.*;

@Getter @Setter
public class SignupRequest {
    @NotBlank(message = "사용자명은 필수 입력입니다.")  // [Validation]
    @Size(min = 3, max = 20, message = "사용자명은 3~20자로 입력해야 합니다.") // [Validation]
    private String username;

    @NotBlank(message = "비밀번호는 필수 입력입니다.")  // [Validation]
    @Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.") // [Validation]
    private String password;

    @NotBlank(message = "이메일은 필수 입력입니다.") // [Validation]
    @Email(message = "올바른 이메일 형식이 아닙니다.") // [Validation]
    private String email;
}