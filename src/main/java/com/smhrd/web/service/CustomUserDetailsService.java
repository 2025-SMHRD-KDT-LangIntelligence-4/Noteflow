package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public CustomUserDetailsService(UserRepository userRepo,
                                    PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // 로그인 처리
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + userId));

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getUserRole().toUpperCase())
        );

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUserId())
                .password(user.getUserPw())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    // 회원가입 메서드
    public User register(String userId, String rawPassword, String email, boolean mailingAgreed) {
        User user = User.builder()
                .userId(userId)
                .userPw(passwordEncoder.encode(rawPassword))
                .userRole("USER")
                .email(email)
                .mailingAgreed(mailingAgreed)
                .createdAt(LocalDateTime.now())
                .attachmentCount(0)
                .build();

        return userRepo.save(user);
    }
}
