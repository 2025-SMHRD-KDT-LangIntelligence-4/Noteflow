package com.smhrd.dto;

import jakarta.validation.constraints.*;  // [추가] Validation 라이브러리
import lombok.*;

@Data
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}