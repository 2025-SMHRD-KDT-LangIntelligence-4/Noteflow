package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // ✅ 추가
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
@Slf4j // ✅ 추가
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService; // ✅ 추가

    // 회원가입 로직: 이메일 인증 기능 추가 ✅
    @Transactional
    public User signup(User user) {
        if (userRepo.findByUserId(user.getUserId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }
        if (userRepo.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 이메일입니다.");
        }
        if (userRepo.findByNickname(user.getNickname()).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 닉네임입니다.");
        }

        user.setUserPw(passwordEncoder.encode(user.getUserPw()));
        user.setUserRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        
        // ✅ 이메일 인증 필수 설정 추가
        user.setEmailVerified(false);    // 미인증 상태
        user.setAccountEnabled(false);   // 계정 비활성화
        
        if (user.getAttachmentCount() == null) user.setAttachmentCount(0);
        if (user.getMailingAgreed() == null) user.setMailingAgreed(false);
        
        User savedUser = userRepo.save(user);

        // ✅ 인증 이메일 발송
        try {
            emailService.sendSignupVerificationEmail(user.getEmail(), user.getNickname());
            log.info("회원가입 인증 메일 발송 성공: {}", user.getEmail());
        } catch (Exception e) {
            log.error("회원가입 인증 메일 발송 실패: {} - {}", user.getEmail(), e.getMessage());
            // 메일 발송 실패해도 회원가입은 완료 (나중에 재발송 가능)
        }

        return savedUser;
    }

    // ✅ 이메일 인증 관련 메서드들 추가
    public Optional<User> getUserByEmail(String email) {
        return userRepo.findByEmail(email);
    }

    @Transactional
    public void activateAccount(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        user.setEmailVerified(true);
        user.setAccountEnabled(true);
        userRepo.save(user);
        log.info("계정 활성화 완료: {}", email);
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        user.setUserPw(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        log.info("비밀번호 재설정 완료: {}", email);
    }

    @Transactional
    public void deleteAccountByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 프로필 이미지 삭제
        if (user.getProfileImage() != null) {
            File oldFile = new File(user.getProfileImage());
            if (oldFile.exists()) {
                oldFile.delete();
                log.info("프로필 이미지 삭제 완료: {}", user.getProfileImage());
            }
        }
        
        userRepo.delete(user);
        log.info("계정 삭제 완료: {}", email);
    }

    // ✅ user_idx 기반 조회로 변경 (기존 코드 유지)
    public Optional<User> getUserInfo(Long userIdx) {
        return userRepo.findByUserIdx(userIdx);
    }

    // 중복 검사는 여전히 userId, email 기반으로 유지
    public boolean isUserIdDuplicate(String userId) {
        return userRepo.findByUserId(userId).isPresent();
    }

    public boolean isEmailDuplicate(String email) {
        return userRepo.existsByEmail(email);
    }

    public boolean isNickNameDuplicate(String nickname) {
        return userRepo.findByNickname(nickname).isPresent();
    }

    // ✅ 비밀번호 검증도 user_idx 기반으로 변경 (기존 코드 유지)
    public boolean verifyPassword(Long userIdx, String rawPassword) {
        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        return passwordEncoder.matches(rawPassword, user.getUserPw());
    }

    // ✅ 회원정보 수정도 user_idx 기반으로 변경 (기존 코드 유지)
    @Transactional
    public void updateUserInfo(Long userIdx, String nickname, String userEmail, String userPw,
                               String interestArea, String learningArea,
                               MultipartFile profileImage, Boolean deleteProfileImage) {
        
        User user = userRepo.findByUserIdx(userIdx)
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

    // ✅ 회원탈퇴도 user_idx 기반으로 변경 (기존 코드 유지)
    @Transactional
    public void deleteUserAccount(Long userIdx) {
        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 프로필 이미지 삭제
        if (user.getProfileImage() != null) {
            File oldFile = new File(user.getProfileImage());
            if (oldFile.exists()) oldFile.delete();
        }

        userRepo.delete(user);
    }
}
