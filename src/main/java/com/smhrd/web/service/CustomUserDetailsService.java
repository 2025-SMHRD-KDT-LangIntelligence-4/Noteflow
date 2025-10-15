package com.smhrd.web.service;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import com.smhrd.web.security.CustomUserDetails; // [추가]
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + userId));

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getUserRole().toUpperCase())
        );

        // 기존 코드 → Spring의 기본 User 객체 반환
        // → 수정 코드 → CustomUserDetails 반환
        return new CustomUserDetails(user, authorities); // [수정]
    }
}
