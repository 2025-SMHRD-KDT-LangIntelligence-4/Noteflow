package com.smhrd.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${vllm.api.url}")
    private String vllmApiUrl;

    /**
     * 전역 WebClient 빈 정의
     * 이름 없이 등록하면 모든 @Autowired WebClient 주입에 사용됩니다.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
