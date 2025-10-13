package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.Prompt;
import com.smhrd.web.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VllmApiService {

    private final WebClient vllmWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptService promptService;
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

    // --------------------------
    // 노션 생성
    // --------------------------
    public String generateNotion(String userContent, String promptTitle) {
        try {
            // 선택한 프롬프트의 content 가져오기
            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            WebClient webClient = webClientBuilder
                    .baseUrl(apiUrl)       // 또는 application.properties값 사용
                    .build();
            // vLLM API 요청 데이터 구성
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("model", "your-model-name"); // 🔥 실제 모델명으로 변경 필요

            // 시스템 메시지와 사용자 입력 조합
            String fullPrompt = prompt.getContent() + "\n\n" + userContent;

            List<Map<String, String>> messages = Arrays.asList(
                    Map.of("role", "user", "content", fullPrompt)
            );

            requestData.put("messages", messages);
            requestData.put("max_tokens", 2000);
            requestData.put("temperature", 0.7);

            // vLLM API 호출
            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // JSON 파싱해서 content 추출
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response);

            return jsonNode.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("AI 요약 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    // --------------------------
    // vLLM API 호출
    // --------------------------
    private String callVllmApi(String prompt) {
        try {
        	Map<String, Object> requestData = buildChatApiRequest(prompt);

            String response = vllmWebClient
                    .post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(60000))
                    .block();

            return extractChatGeneratedText(response);

        } catch (Exception e) {
            return "AI 서비스를 일시적으로 사용할 수 없습니다. 나중에 다시 시도해주세요.";
        }
    }

    // --------------------------
    // 프롬프트 빌드
    // --------------------------
    private String buildNotionPrompt(String originalText, String notionType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 텍스트를 '").append(notionType).append("' 스타일로 변환해주세요.\n\n");

        // DB에서 가져오기
        String instruction = promptService.getInstruction(notionType);
        prompt.append(instruction).append("\n\n");

        prompt.append("원본 텍스트:\n").append(originalText);
        prompt.append("\n\n변환된 노션을 한국어로 작성해주세요:");

        return prompt.toString();
    }

    // --------------------------
    // 유틸
    // --------------------------
    private Map<String, Object> buildChatApiRequest(String prompt) {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("model", modelName);
        requestData.put("max_tokens", maxTokens);
        requestData.put("temperature", temperature);
        requestData.put("stream", false);

        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", prompt);

        requestData.put("messages", List.of(user));
        return requestData;
    }

    private String extractChatGeneratedText(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
            	JsonNode content = choices.get(0).path("message").path("content");
            	return content.isMissingNode() ? "" : content.asText();
            }
            return "AI 응답을 파싱할 수 없습니다.";

        } catch (Exception e) {
            return "AI 응답 처리하는 중에 오류가 발생했습니다.";
        }
    }


    public String generateResponse(String contextualPrompt) {
        // 1) 시스템 메시지(기본 지침) 로드
        String systemInstruction = promptService.getInstruction("chat_system");
        // 2) 포맷용 프롬프트 로드 (선택적)
        String formatInstruction = promptService.getInstruction("chat_format");
        // 3) 전체 프롬프트 조합
        String combinedPrompt = systemInstruction
                + "\n\n" + formatInstruction
                + "\n\n" + contextualPrompt;
        // 4) vLLM API 호출
        return callVllmApi(combinedPrompt);
    }

    public String processContent(String content, String notionType) {
        try {
            // DB에서 notionType에 해당하는 Prompt 조회
            Prompt prompt = promptRepository.findByTitle(notionType)
                    .orElseThrow(() -> new IllegalArgumentException("프롬프트를 찾을 수 없습니다: " + notionType));


            String fullPrompt = prompt.getContent() + "\n\n" + content;
            Map<String, Object> req = new HashMap<>();
            req.put("model", modelName);
            req.put("max_tokens", maxTokens);
            req.put("temperature", temperature);
            req.put("stream", false);
            Map<String, String> user = new HashMap<>();
            user.put("role", "user");
            user.put("content", fullPrompt);
            req.put("messages", List.of(user));
            String response = vllmWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(req)
                   .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            return extractChatGeneratedText(response);

        } catch (Exception e) {
            throw new RuntimeException("vLLM API 호출 실패: " + e.getMessage(), e);
        }
    }
}