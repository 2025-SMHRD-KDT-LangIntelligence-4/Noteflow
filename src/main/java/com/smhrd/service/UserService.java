package com.smhrd.service;

import com.smhrd.config.JwtUtil;
import com.smhrd.dto.LoginRequest;
import com.smhrd.dto.SignupRequest;
import com.smhrd.model.entity.User;
import com.smhrd.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // 회원가입
    public String signup(SignupRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .build();
        userRepository.save(user);
        return "회원가입 성공";
    }

    // 로그인 후 JWT 발급
    public String login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            if (passwordEncoder.matches(request.getPassword(), u.getPassword())) {

                // UserDetails 생성 (Spring Security용)
                UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                        u.getEmail(),                        // username
                        u.getPassword(),                     // password
                        Collections.singletonList(() -> "ROLE_USER") // 권한
                );

                // JWT 발급
                return jwtUtil.generateToken(userDetails);
            }
        }
        throw new RuntimeException("로그인 실패");
    }

    // 사용자 정보 조회 (캐시 적용)
    @Cacheable(value = "userCache", key = "#email")
    public User getUserInfo(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));
    }
}
