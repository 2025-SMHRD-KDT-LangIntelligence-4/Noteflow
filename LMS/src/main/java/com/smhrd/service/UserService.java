package com.smhrd.service;

import com.smhrd.config.JwtUtil;
import com.smhrd.dto.*;
import com.smhrd.model.entity.User;
import com.smhrd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  // [추가] 로그 기능
import org.springframework.cache.annotation.Cacheable;  // [추가] 캐시 기능
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j   // [추가] 로그 출력 → System.out.println 대신 사용
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public void signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new RuntimeException("이미 존재하는 사용자명입니다.");
        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("이미 등록된 이메일입니다.");

        User user = User.builder() // [빌더 적용]
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .build();
        userRepository.save(user);

        log.info("신규 회원가입: {}", request.getUsername()); // [로그 출력]
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");

        String token = jwtUtil.generateToken(user.getUsername());
        log.info("로그인 성공: {}", request.getUsername()); // [로그 출력]
        return token;
    }

    @Cacheable(value = "userInfo", key = "#username") // [캐시 적용] username 기준 캐싱
    public UserResponse getUserInfo(String username) {
        log.info("DB 조회 발생 - 사용자 정보 로드: {}", username); // 캐시 덕분에 매번 찍히지 않음
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }

    public void updateUser(String username, SignupRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        log.info("회원정보 수정 완료: {}", username); // [로그 출력]
    }
}