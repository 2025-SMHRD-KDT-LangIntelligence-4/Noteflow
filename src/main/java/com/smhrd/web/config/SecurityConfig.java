package com.smhrd.web.config;

import com.smhrd.web.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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

    // ---------------------------------------------------
    // 인증 제공자 등록 (CustomUserDetailsService 연동)
    // ---------------------------------------------------
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(false); // 디버깅 시 UsernameNotFoundException 확인용
        return provider;
    }

    // ---------------------------------------------------
    // Security Filter Chain 설정
    // ---------------------------------------------------
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 인증 제공자 등록
            .authenticationProvider(authenticationProvider())
            .csrf(csrf -> csrf
                    // 벡터 테스트 API는 CSRF 체크 제외
                    .ignoringRequestMatchers("/admin/vector-test/**")
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                
            		
            		)
            // URL 접근 제어
            .authorizeHttpRequests(auth -> auth
                // 로그인/회원가입 페이지 및 정적 자원 접근 허용
                .requestMatchers(HttpMethod.GET, "/login", "/signup").permitAll()
                .requestMatchers(HttpMethod.POST, "/login", "/signup").permitAll()
                .requestMatchers("/main","/css/**", "/js/**", "/images/**", "/fonts/**", "/static/**", "/", "/webjars/**","/templates/fragments/**","/admin/vector-test/**","/api/video/stream/**").permitAll()
                // 그 외 요청은 인증 필요
                .anyRequest().authenticated()
            )

            // 폼 로그인 설정
            .formLogin(form -> form
                .loginPage("/login")                          // 커스텀 로그인 페이지
                .loginProcessingUrl("/perform_login")                 // 로그인 POST 처리 URL
                .usernameParameter("user_id")                 // HTML form input name
                .passwordParameter("user_pw")                 // HTML form input name
                .defaultSuccessUrl("/main", true)             // 로그인 성공 시 이동 경로
                .failureUrl("/login?error")                   // 실패 시 리다이렉트 경로
                .permitAll()
            )
            // 세션 관리
            .sessionManagement(session -> session
                    .maximumSessions(1)  // 동시 세션 1개로 제한
                    .maxSessionsPreventsLogin(false)  // 새 로그인 허용, 기존 세션 무효화
                    .expiredUrl("/login?expired")  // 기존 세션 만료 시 리다이렉트
            )
            // 로그아웃 설정
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin  // 같은 도메인에서는 iframe 허용
                        ))
        ;

            // CSRF 설정 (필요 시 비활성화 가능)


        return http.build();
    }


    // ---------------------------------------------------
    // 정적 리소스 Security 필터 제외
    // ---------------------------------------------------
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
                "/css/**", "/js/**", "/images/**", "/fonts/**", "/webjars/**", "/favicon.ico","/node_modules/**"
        );}
    // 세션이벤트 관리
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
