package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VllmApiService {

    private final WebClient vllmWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptService promptService;

    @Value("${vllm.api.model}")
    private String modelName;

    @Value("${vllm.api.max-tokens}")
    private int maxTokens;

    @Value("${vllm.api.temperature}")
    private double temperature;

    // --------------------------
    // 노션 생성
    // --------------------------
    public String generateNotion(String originalText, String notionType) {
        String prompt = buildNotionPrompt(originalText, notionType);
        return callVllmApi(prompt);
    }

    // --------------------------
    // vLLM API 호출
    // --------------------------
    private String callVllmApi(String prompt) {
        try {
            Map<String, Object> requestData = buildApiRequest(prompt);

            String response = vllmWebClient
                    .post()
                    .bodyValue(requestData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(60000))
                    .block();

            return extractGeneratedText(response);

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
    private Map<String, Object> buildApiRequest(String prompt) {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("model", modelName);
        requestData.put("max_tokens", maxTokens);
        requestData.put("temperature", temperature);
        requestData.put("stream", false);

        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        requestData.put("messages", List.of(message));
        return requestData;
    }

    private String extractGeneratedText(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    return content != null ? content.asText() : "";
                }
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

}