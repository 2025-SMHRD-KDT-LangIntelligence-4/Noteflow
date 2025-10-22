package com.smhrd.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // 기존 vLLM (메인)
    @Value("${vllm.api.url}")
    private String vllmApiUrl;

    // 챗봇 vLLM (신규)
    @Value("${vllm.chatbot.url}")
    private String chatbotUrl;

    // 임베딩 (신규)
    @Value("${embedding.url}")
    private String embeddingUrl;

    @Bean(name = "vllmApiClient")
    public WebClient vllmApiClient() {
        return WebClient.builder()
                .baseUrl(vllmApiUrl)
                .build();
    }

    @Bean(name = "vllmChat")
    public WebClient vllmChat() {
        return WebClient.builder()
                .baseUrl(chatbotUrl)
                .build();
    }

    @Bean(name = "embeddingClient")
    public WebClient embeddingClient() {
        return WebClient.builder()
                .baseUrl(embeddingUrl)
                .build();
    }
}
