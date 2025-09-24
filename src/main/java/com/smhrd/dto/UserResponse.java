package com.smhrd.dto;

import jakarta.validation.constraints.*;  // [추가] Validation 라이브러리
import lombok.*;

@Getter @Setter @AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
}