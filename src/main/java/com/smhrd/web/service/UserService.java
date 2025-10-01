package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    // --------------------------
    // 회원가입 처리
    // --------------------------
    public User signup(User user) {
        // 아이디 중복 체크
        if (userRepo.findByUserId(user.getUserId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        // 비밀번호 암호화
        user.setUserPw(passwordEncoder.encode(user.getUserPw()));

        // 기본값 세팅
        user.setUserRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setLastLogin(null);
        if (user.getAttachmentCount() == null) user.setAttachmentCount(0);
        if (user.getMailingAgreed() == null) user.setMailingAgreed(false);

        // DB 저장
        return userRepo.save(user);
    }

    // --------------------------
    // 마이페이지 정보 조회
    // --------------------------
    public Optional<User> getUserInfo(String userId) {
        return userRepo.findByUserId(userId);
    }

    // --------------------------
    // 아이디 중복 체크 (AJAX)
    // --------------------------
    public boolean isUserIdDuplicate(String userId) {
        return userRepo.findByUserId(userId).isPresent();
    }
}
