package com.smhrd.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * vLLM 호출용 WebClient를 baseUrl 포함으로 통일해 둡니다.
 */
@Configuration
public class WebClientConfig {

    @Value("${vllm.api.url}")
    private String vllmApiUrl;

    @Bean
    public WebClient vllmWebClient() {
        return WebClient.builder()
                .baseUrl(vllmApiUrl)              // 예: http://localhost:8000
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
