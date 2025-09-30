package com.smhrd.dto;

import jakarta.validation.constraints.*;  // [추가] Validation 라이브러리
import lombok.*;

@Data
public class UserResponse {
    private String email;
    private String name;
}