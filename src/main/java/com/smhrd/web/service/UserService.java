package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    // --- 파일 경로 상수 정의 및 통일 ---
    // 프로젝트 루트 기준 정적 리소스의 물리적 경로 루트
    private static final String STATIC_ROOT_DIR = "src/main/resources/static/";
    // DB에 저장되는 웹 접근 경로 (컨텍스트 경로 제외, 예: images/profile/)
    private static final String PROFILE_SUB_DIR = "images/profile/"; 
    // 프로필 이미지의 최종 물리적 저장 경로 (Path 객체)
    private static final Path UPLOAD_PATH = Paths.get(STATIC_ROOT_DIR + PROFILE_SUB_DIR);
    // --- 파일 경로 상수 정의 끝 ---

    // 회원가입 로직: userId, email 중복 검사는 그대로 유지
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
        if (user.getAttachmentCount() == null) user.setAttachmentCount(0);
        // bio와 mailingAgreed는 회원가입 시에는 default 값으로 처리되거나 DB 스키마에 따라 null이 될 수 있습니다.
        if (user.getMailingAgreed() == null) user.setMailingAgreed(false);

        return userRepo.save(user);
    }

    // ✅ user_idx 기반 조회로 변경 (ViewController에서 사용)
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

    // ✅ 비밀번호 검증도 user_idx 기반으로 변경
    public boolean verifyPassword(Long userIdx, String rawPassword) {
        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        return passwordEncoder.matches(rawPassword, user.getUserPw());
    }

    // ✅ 회원정보 수정 로직 (프로필 이미지, bio, mailingAgreed 포함)
    @Transactional
    public void updateUserInfo(Long userIdx, String nickname, String userEmail, String userPw,
                               String interestArea, String learningArea, String bio, Boolean mailingAgreed, // [추가된 파라미터]
                               MultipartFile profileImage, Boolean deleteProfileImage) {

        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 1. 기본 정보 업데이트
        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname);
        if (userEmail != null && !userEmail.isBlank()) user.setEmail(userEmail);
        if (userPw != null && !userPw.isBlank()) user.setUserPw(passwordEncoder.encode(userPw));
        if (interestArea != null && !interestArea.isBlank()) user.setInterestArea(interestArea);
        if (learningArea != null && !learningArea.isBlank()) user.setLearningArea(learningArea);
        
        // 2. 추가 정보 업데이트
        if (bio != null) user.setBio(bio); // bio는 빈 문자열로도 업데이트 가능
        // mailingAgreed는 체크박스가 체크되지 않으면 null로 오므로, null이면 false로 처리
        user.setMailingAgreed(Boolean.TRUE.equals(mailingAgreed)); 


        // 3. 프로필 이미지 처리
        try {
            if (!Files.exists(UPLOAD_PATH)) {
                Files.createDirectories(UPLOAD_PATH);
            }
        } catch (IOException e) {
            throw new RuntimeException("프로필 이미지 저장 경로 생성 실패", e);
        }

        String currentImagePath = user.getProfileImage(); // DB에 저장된 현재 이미지 경로 (예: images/profile/a.jpg)
        
        // 3-1. 이미지 삭제 요청 처리
        if (Boolean.TRUE.equals(deleteProfileImage)) {
            if (currentImagePath != null) {
                // 기존 파일 시스템에서 물리적 파일 삭제
                // 파일명만 추출하여 UPLOAD_PATH에 결합해 정확한 물리적 경로를 얻습니다.
                String fileName = Paths.get(currentImagePath).getFileName().toString();
                Path oldFilePath = UPLOAD_PATH.resolve(fileName);
                try {
                    Files.deleteIfExists(oldFilePath);
                } catch (IOException e) {
                    System.err.println("기존 프로필 이미지 파일 삭제 실패: " + oldFilePath);
                    // 파일 삭제 실패는 DB 업데이트를 막지 않음
                }
            }
            user.setProfileImage(null); // DB 필드 초기화
        }

        // 3-2. 새로운 프로필 이미지 업로드 처리
        if (profileImage != null && !profileImage.isEmpty()) {
            // 새 파일 업로드 시, 기존 파일 삭제 (deleteProfileImage와 무관하게)
            if (currentImagePath != null && !Boolean.TRUE.equals(deleteProfileImage)) {
                String fileName = Paths.get(currentImagePath).getFileName().toString();
                Path oldFilePath = UPLOAD_PATH.resolve(fileName);
                try {
                    Files.deleteIfExists(oldFilePath);
                } catch (IOException e) {
                    System.err.println("새 이미지 업로드 전, 기존 프로필 이미지 파일 삭제 실패: " + oldFilePath);
                }
            }

            // 새 파일 저장
            String originalFileName = profileImage.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;
            Path destPath = UPLOAD_PATH.resolve(fileName); // UPLOAD_PATH 사용
            
            try {
                profileImage.transferTo(destPath.toFile());
                // DB에는 웹 접근 경로 저장 (예: images/profile/uuid.jpg)
                user.setProfileImage(PROFILE_SUB_DIR + fileName); 
            } catch (IOException e) {
                throw new RuntimeException("새 프로필 이미지 업로드 실패", e);
            }
        }

        userRepo.save(user);
    }

    // ✅ 회원탈퇴도 user_idx 기반으로 변경
    @Transactional
    public void deleteUserAccount(Long userIdx) {
        User user = userRepo.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        
        // 회원의 프로필 이미지가 있으면 물리적 파일도 삭제
        String profileImagePath = user.getProfileImage();
        if (profileImagePath != null) {
             // 파일명만 추출하여 UPLOAD_PATH에 결합해 정확한 물리적 경로를 얻습니다.
             String fileName = Paths.get(profileImagePath).getFileName().toString();
             Path filePathToDelete = UPLOAD_PATH.resolve(fileName);
             try {
                 Files.deleteIfExists(filePathToDelete);
             } catch (IOException e) {
                 System.err.println("회원 탈퇴 중 프로필 이미지 파일 삭제 실패: " + filePathToDelete);
             }
        }
        
        userRepo.delete(user);
    }

	
}
