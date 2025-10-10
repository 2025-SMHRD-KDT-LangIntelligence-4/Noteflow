package com.smhrd.web.config;

import com.smhrd.web.service.CustomUserDetailsService;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
//                .csrf(csrf -> csrf.disable())

                // 인증 제공자 등록
                .authenticationProvider(authenticationProvider())

                // URL 접근 제어
                .authorizeHttpRequests(auth -> auth
                        // 로그인 폼(GET)과 로그인 처리(POST)를 모두 허용
                        .requestMatchers(HttpMethod.GET,  "/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()

                        // 회원가입, 정적 자원
                        .requestMatchers("/signup", "/css/**", "/js/**","/","/**","/static/**").permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // 폼 로그인 설정 (permitAll()로 GET/POST /login 자동 허용)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("user_id")
                        .passwordParameter("user_pw")
                        .defaultSuccessUrl("/main", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                	    .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                	    .logoutSuccessUrl("/login?logout=true")
                	    .invalidateHttpSession(true)
                	    .deleteCookies("JSESSIONID")
                	    .permitAll()
                	)
        ;

        return http.build();
    }


}
