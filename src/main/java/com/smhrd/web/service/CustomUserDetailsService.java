package com.smhrd.web.service;

import com.smhrd.web.domain.User;
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

    // Spring Security 로그인 처리
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + username));

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + u.getRole().toUpperCase())
        );

        return new org.springframework.security.core.userdetails.User(
                u.getUsername(),
                u.getPassword(),
                true, // enabled always true
                true, true, true,
                authorities
        );
    }

    // 회원가입 메서드
    public User register(String username, String rawPassword, String email, boolean mailingAgreed) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole("USER");
        u.setEmail(email);
        u.setMailingAgreed(mailingAgreed);
        u.setCreatedAt(LocalDateTime.now());
        // 기타 필드 기본값 설정
        return userRepo.save(u);
    }
}
