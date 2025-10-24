package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import com.smhrd.web.security.CustomUserDetails;
import org.springframework.security.authentication.AccountStatusException; // ✅ 추가
import org.springframework.security.authentication.DisabledException; // ✅ 추가
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j; // ✅ 추가
import java.util.List;

@Service
@Slf4j // ✅ 추가
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public CustomUserDetailsService(UserRepository userRepo,
                                    PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        log.debug("로그인 시도: {}", userId); // ✅ 추가
        
        User user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + userId));

        // ✅ 이메일 인증 상태 확인 추가
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            log.warn("이메일 미인증 사용자 로그인 시도: {}", userId);
            throw new AccountStatusException("이메일 인증이 완료되지 않았습니다. 메일함을 확인해주세요.") {};
        }

        // ✅ 계정 활성화 상태 확인 추가
        if (!Boolean.TRUE.equals(user.getAccountEnabled())) {
            log.warn("비활성화된 계정 로그인 시도: {}", userId);
            throw new DisabledException("계정이 비활성화되었습니다. 관리자에게 문의하세요.");
        }

        // ✅ 계정 정지 상태 확인 추가 (추가 보안)
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            log.warn("정지된 계정 로그인 시도: {}", userId);
            throw new DisabledException("계정이 정지되었습니다. 관리자에게 문의하세요.");
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getUserRole().toUpperCase())
        );

        log.info("로그인 성공: {} (userIdx: {})", userId, user.getUserIdx()); // ✅ 추가

        // 기존 코드 그대로 유지
        return new CustomUserDetails(user, authorities);
    }
}
