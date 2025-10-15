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

    // 회원가입 로직: userId, email 중복 검사는 그대로 유지
    public User signup(User user) {
        if (userRepo.findByUserId(user.getUserId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        if (userRepo.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }

        user.setUserPw(passwordEncoder.encode(user.getUserPw()));
        user.setUserRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        if (user.getAttachmentCount() == null) user.setAttachmentCount(0);
        if (user.getMailingAgreed() == null) user.setMailingAgreed(false);

        return userRepo.save(user);
    }

    // ✅ user_idx 기반 조회로 변경
    public Optional<User> getUserInfo(Long userIdx) { // 기존 String userId → Long userIdx [수정]
        return userRepo.findByUserIdx(userIdx); // findByUserId → findByUserIdx [수정]
    }

    // 중복 검사는 여전히 userId, email 기반으로 유지
    public boolean isUserIdDuplicate(String userId) {
        return userRepo.findByUserId(userId).isPresent();
    }

    public boolean isEmailDuplicate(String email) {
        return userRepo.existsByEmail(email);
    }

    // ✅ 비밀번호 검증도 user_idx 기반으로 변경
    public boolean verifyPassword(Long userIdx, String rawPassword) { // [수정]
        User user = userRepo.findByUserIdx(userIdx) // findByUserId → findByUserIdx [수정]
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        return passwordEncoder.matches(rawPassword, user.getUserPw());
    }

    // ✅ 회원정보 수정도 user_idx 기반으로 변경
    @Transactional
    public void updateUserInfo(Long userIdx, String nickname, String userEmail, String userPw,
                               String interestArea, String learningArea,
                               MultipartFile profileImage, Boolean deleteProfileImage) { // [수정]

        User user = userRepo.findByUserIdx(userIdx) // findByUserId → findByUserIdx [수정]
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname);
        if (userEmail != null && !userEmail.isBlank()) user.setEmail(userEmail);
        if (userPw != null && !userPw.isBlank()) user.setUserPw(passwordEncoder.encode(userPw));
        if (interestArea != null && !interestArea.isBlank()) user.setInterestArea(interestArea);
        if (learningArea != null && !learningArea.isBlank()) user.setLearningArea(learningArea);

        String uploadDir = "uploads/profile/";
        File uploadPath = new File(uploadDir);
        if (!uploadPath.exists()) uploadPath.mkdirs();

        if (Boolean.TRUE.equals(deleteProfileImage) && user.getProfileImage() != null) {
            File oldFile = new File(user.getProfileImage());
            if (oldFile.exists()) oldFile.delete();
            user.setProfileImage(null);
        }

        if (profileImage != null && !profileImage.isEmpty()) {
            if (user.getProfileImage() != null) {
                File oldFile = new File(user.getProfileImage());
                if (oldFile.exists()) oldFile.delete();
            }
            String fileName = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
            File dest = new File(uploadDir, fileName);
            try {
                profileImage.transferTo(dest);
                user.setProfileImage(uploadDir + fileName);
            } catch (IOException e) {
                throw new RuntimeException("프로필 이미지 업로드 실패", e);
            }
        }

        userRepo.save(user);
    }

    // ✅ 회원탈퇴도 user_idx 기반으로 변경
    @Transactional
    public void deleteUserAccount(Long userIdx) { // [수정]
        User user = userRepo.findByUserIdx(userIdx) // findByUserId → findByUserIdx [수정]
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        userRepo.delete(user);
    }
}
