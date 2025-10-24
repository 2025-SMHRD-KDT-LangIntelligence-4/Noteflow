package com.smhrd.web.security;

import com.smhrd.web.entity.User;
import com.smhrd.web.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // CustomUserDetails에서 userIdx 추출
        if (authentication.getPrincipal() instanceof CustomUserDetails principal) {
            Long userIdx = principal.getUserIdx();
            
            // last_login과 login_count 업데이트
            Optional<User> userOpt = userRepository.findByUserIdx(userIdx);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setLastLogin(LocalDateTime.now());
                user.setLoginCount(user.getLoginCount() == null ? 1 : user.getLoginCount() + 1);
                userRepository.save(user);
                
                log.info("Updated last_login for user: {} (userIdx: {})", user.getUserId(), userIdx);
            } else {
                log.warn("User not found for userIdx: {}", userIdx);
            }
        }
        
        // 기본 성공 핸들러로 리다이렉트 처리 (원래 요청한 페이지로 이동)
        new SavedRequestAwareAuthenticationSuccessHandler().onAuthenticationSuccess(request, response, authentication);
    }
}