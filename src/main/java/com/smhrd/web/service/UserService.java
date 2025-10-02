package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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

    // --------------------------
    // 마이페이지 정보 수정
    // --------------------------
    @Transactional
    public void updateUserInfo(String userId,
                               String nickname,
                               String userEmail,
                               String userPw,
                               MultipartFile profileImage) {

        User user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 닉네임 수정
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname);
        }

        // 이메일 수정
        if (userEmail != null && !userEmail.isBlank()) {
            user.setEmail(userEmail);
        }

        // 비밀번호 수정
        if (userPw != null && !userPw.isBlank()) {
            user.setUserPw(passwordEncoder.encode(userPw));
        }

        // 프로필 이미지 업로드
        if (profileImage != null && !profileImage.isEmpty()) {
            // 기존 이미지 삭제
            if (user.getProfileImageUrl() != null) {
                File oldFile = new File(user.getProfileImageUrl());
                if (oldFile.exists()) {
                    oldFile.delete();
                }
            }

            // 새 이미지 저장
            String uploadDir = "uploads/profile/";
            String fileName = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
            File dest = new File(uploadDir, fileName);

            try {
                profileImage.transferTo(dest);
                user.setProfileImageUrl(uploadDir + fileName);
            } catch (IOException e) {
                throw new RuntimeException("프로필 이미지 업로드 실패", e);
            }
        }

        userRepo.save(user);
    }
}
