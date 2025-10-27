package com.smhrd.web.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

/**
 * RestTemplate Bean 설정
 *
 * RestTemplate을 Bean으로 등록해야 DataMigrationService에서 자동 주입 가능
 * 외부 API (Embedding 서버) 호출용
 */
@Configuration
public class RestTemplateConfig {

    /**
     * ✅ RestTemplate Bean 등록
     * - 기본 RestTemplate: HTTP 요청/응답 처리
     * - 타임아웃 설정: 연결 10초, 읽기 10초
     * - 재시도 로직은 별도로 필요하면 추가
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))  // 연결 타임아웃
                .setReadTimeout(Duration.ofSeconds(10))     // 읽기 타임아웃
                .build();
    }

    /**
     * ✅ Embedding 서버 전용 RestTemplate
     * - Embedding API 호출 최적화
     * - 타임아웃: 5초 (빠른 응답 기대)
     */
    @Bean(name = "embeddingRestTemplate")
    public RestTemplate embeddingRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * ✅ VLLM 서버 전용 RestTemplate
     * - 모델 응답 시간이 길 수 있음 (20-30초)
     * - 타임아웃: 60초 (넉넉하게 설정)
     */
    @Bean(name = "vllmRestTemplate")
    public RestTemplate vllmRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}