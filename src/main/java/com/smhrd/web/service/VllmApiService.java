package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.Prompt;
import com.smhrd.web.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VllmApiService {

    private final WebClient vllmWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptRepository promptRepository;

    private final WebClient.Builder webClientBuilder;

    @Value("${vllm.api.model}")
    private String modelName;

    @Value("${vllm.api.max-tokens}")
    private int maxTokens;

    @Value("${vllm.api.temperature}")
    private double temperature;

    @Value("${vllm.api.url}")
    private String apiUrl;

    /**
     * 노션 생성: DB 프롬프트 + 사용자 입력을 합쳐 마크다운 전문을 반환
     */
    public String generateNotion(String userContent, String promptTitle) {
        try {
            // 1. DB에서 프롬프트 조회
            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            String fullPrompt = prompt.getContent() + "\n\n" + userContent;

            // 2. 요청 데이터 구성
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("model", modelName);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "You are a helpful assistant."),
                    Map.of("role", "user", "content", fullPrompt)
            );
            requestData.put("messages", messages);
            requestData.put("max_tokens", maxTokens);
            requestData.put("temperature", temperature);

            // 3. vLLM API 호출
            String response = webClientBuilder.baseUrl(apiUrl).build()
                    .post().uri("/v1/chat/completions")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestData)
                    .retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response == null) {
                throw new RuntimeException("vLLM 서버가 빈 응답을 반환했습니다.");
            }

            // 4. JSON 파싱
            JsonNode root = objectMapper.readTree(response);
            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            return contentNode.asText();

        } catch (Exception e) {
            throw new RuntimeException("AI 요약 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 일반 챗봇 호출: 컨텍스트 프롬프트 전송 후 간단 답변 반환
     */
    public String generateResponse(String contextualPrompt) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("model", modelName);
            req.put("max_tokens", Math.min(maxTokens, 800));
            req.put("temperature", 1.0);
            req.put("stream", false);

            String system = "You are a helpful assistant. Answer in Korean.";
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", contextualPrompt)
            );
            req.put("messages", messages);

            String response = vllmWebClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            return contentNode.asText();

        } catch (Exception e) {
            return "AI 서비스 호출 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    /**
     * 단순 호출: DB 프롬프트 조회 없이 사용자 입력만 전달
     */
    public String processContent(String content, String notionType) {
        try {
            Prompt prompt = promptRepository.findByTitle(notionType)
                    .orElseThrow(() -> new IllegalArgumentException("프롬프트를 찾을 수 없습니다: " + notionType));

            String fullPrompt = prompt.getContent() + "\n\n" + content;
            return generateNotion(fullPrompt, notionType);

        } catch (Exception e) {
            throw new RuntimeException("vLLM API 호출 실패: " + e.getMessage(), e);
        }
    }
}
