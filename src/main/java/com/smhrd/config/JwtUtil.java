package com.smhrd.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtil
 * ----------------------
 * - JWT 토큰 생성 및 검증 담당
 * - HS256 알고리즘 사용 (대칭키 기반)
 * - 만료 시간은 예시로 1시간(3600000ms) 설정
 */
@Component
public class JwtUtil {

    // [비밀키] - 실제 배포 환경에서는 환경변수나 설정 파일에서 주입받아야 함
    private static final String SECRET_KEY = "my-secret-key-for-jwt-token-which-should-be-long";

    // JWT 만료 시간: 1시간 (3600000ms)
    private static final long EXPIRATION_TIME = 1000 * 60 * 60;

    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    /**
     * 토큰 생성
     * @param username 사용자 이름
     * @return JWT 토큰 문자열
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)  // 토큰에 저장할 정보
                .setIssuedAt(new Date())  // 발급 시간
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 만료 시간
                .signWith(key, SignatureAlgorithm.HS256) // 서명
                .compact();
    }

    /**
     * 토큰에서 사용자 이름 추출
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 토큰 만료 여부 확인
     */
    public boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token, String username) {
        return (extractUsername(token).equals(username) && !isTokenExpired(token));
    }

    /**
     * Claims 파싱 (토큰 본문)
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)  // 서명키 지정
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
