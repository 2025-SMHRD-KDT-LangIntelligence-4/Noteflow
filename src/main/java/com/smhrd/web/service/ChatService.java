package com.smhrd.web.service;

import com.smhrd.web.controller.ChatController;
import com.smhrd.web.entity.Chat;
import com.smhrd.web.repository.ChatRepository;
import com.smhrd.web.controller.ChatController.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    @Qualifier("vllmChat")
    private final WebClient vllmChat;

    @Qualifier("embeddingClient")
    private final WebClient embeddingClient;

    private final ChatRepository chatRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${vllm.chatbot.model}")
    private String chatbotModel;

    @Value("${vllm.chatbot.max-tokens}")
    private Integer maxTokens;

    @Value("${vllm.chatbot.temperature}")
    private Double temperature;

    public ChatResponse processChat(Long userIdx, String message, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        long startTime = System.currentTimeMillis();

        // 1. 사용자 질문 벡터화
        List<Float> queryVector = getEmbedding(message);

        // 2. ✅ 타입 변경: List<String> → List<Map<String, Object>>
        List<Map<String, Object>> relevantDocs = searchRelevantDocuments(userIdx, queryVector);

        // 3. MySQL 대화 히스토리 (최근 5턴)
        List<Chat> chatHistory = getChatHistory(sessionId);

        // 4. RAG 프롬프트 생성
        String ragPrompt = buildRagPrompt(message, relevantDocs, chatHistory);

        // 5. vLLM 챗봇 호출
        String botReply = callChatbot(ragPrompt, chatHistory);

        int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

        // 6. DB 저장
        Chat chat = new Chat();
        chat.setUserIdx(userIdx);
        chat.setSessionId(sessionId);
        chat.setQuestion(message);
        chat.setAnswer(botReply);
        chat.setResponseTimeMs(responseTimeMs);
        chatRepository.save(chat);

        int historyCount = chatRepository.countBySessionId(sessionId);

        return new ChatResponse(botReply, historyCount, sessionId);
    }

    // 임베딩 생성
    private List<Float> getEmbedding(String text) {
        try {
            Map<String, Object> response = embeddingClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("texts", List.of(text)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            return embeddings.get(0).stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // MongoDB Vector 검색 (사용자 노트/문서)
    private List<Map<String, Object>> searchRelevantDocuments(Long userIdx, List<Float> queryVector) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("user_idx").is(userIdx));
            query.limit(3);

            List<Document> docs = mongoTemplate.find(query, Document.class, "user_notes");

            System.out.println("🔍 RAG 검색 - user_idx: " + userIdx + ", 검색된 문서: " + docs.size() + " 개");
            docs.forEach(doc -> {
                System.out.println("📄 문서: title=" + doc.get("title") + ", content=" +
                        (doc.get("content") != null ? doc.get("content").toString().substring(0, Math.min(50, doc.get("content").toString().length())) : "null"));
            });

            return docs.stream()
                    .map(doc -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("title", doc.get("title"));
                        result.put("content", doc.get("content"));
                        result.put("created_at", doc.get("created_at"));
                        result.put("tags", doc.get("tags"));
                        result.put("category", doc.get("category"));
                        return result;
                    })
                    .limit(3)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of();
        }
    }

    // 코사인 유사도 계산
    private double cosineSimilarity(List<Float> vec1, List<Double> vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < Math.min(vec1.size(), vec2.size()); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // 대화 히스토리 가져오기
    private List<Chat> getChatHistory(String sessionId) {
        List<Chat> allHistory = chatRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int total = allHistory.size();
        return allHistory.stream()
                .skip(Math.max(0, total - 5))  // 최근 5턴
                .collect(Collectors.toList());
    }

    // RAG 프롬프트 생성
    private String buildRagPrompt(String userQuestion, List<Map<String, Object>> relevantDocs, List<Chat> history) {
        StringBuilder prompt = new StringBuilder();

        if (!relevantDocs.isEmpty()) {
            prompt.append("다음은 사용자가 공부한 내용입니다:\n\n");
            for (int i = 0; i < relevantDocs.size(); i++) {
                Map<String, Object> doc = relevantDocs.get(i);
                String title = (String) doc.get("title");
                String content = (String) doc.get("content");
                Object createdAt = doc.get("created_at");
                List<String> tags = (List<String>) doc.get("tags");
                String category = (String) doc.get("category");

                if (content != null && content.length() > 150) {
                    content = content.substring(0, 150) + "...";
                }

                prompt.append(String.format("[문서 %d]\n", i + 1));
                prompt.append(String.format("제목: %s\n", title));
                prompt.append(String.format("날짜: %s\n", createdAt));

                if (category != null) {
                    prompt.append(String.format("분류: %s\n", category));
                }

                if (tags != null && !tags.isEmpty()) {
                    prompt.append(String.format("키워드: %s\n", String.join(", ", tags)));
                }

                prompt.append(String.format("내용: %s\n\n", content));
            }
            prompt.append("위 내용을 참고해서 질문에 답변해주세요.\n\n");
        }

        if (!history.isEmpty()) {
            prompt.append("이전 대화:\n");
            int startIdx = Math.max(0, history.size() - 3);
            for (int i = startIdx; i < history.size(); i++) {
                Chat chat = history.get(i);
                String q = chat.getQuestion();
                String a = chat.getAnswer();

                if (q != null && q.length() > 80) q = q.substring(0, 80) + "...";
                if (a != null && a.length() > 80) a = a.substring(0, 80) + "...";

                prompt.append(String.format("사용자: %s\nAI: %s\n\n", q, a));
            }
        }

        prompt.append(String.format("사용자 질문: %s", userQuestion));

        String result = prompt.toString();
        if (result.length() > 1500) {
            result = result.substring(0, 1500) + "\n\n사용자 질문: " + userQuestion;
        }

        System.out.println("📝 RAG 프롬프트 길이: " + result.length() + "자");

        return result;
    }

    // vLLM 챗봇 호출
    private String callChatbot(String ragPrompt, List<Chat> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 시스템 프롬프트
        messages.add(Map.of(
                "role", "system",
                "content", "너는 학습 도우미야. 사용자가 공부한 내용을 기반으로 정확하게 답변해줘. " +
                        "관련 문서가 제공되면 그 내용을 우선 참고하고, 없으면 일반 지식으로 답변해."
        ));

        // RAG 프롬프트 추가
        messages.add(Map.of("role", "user", "content", ragPrompt));

        Map<String, Object> request = Map.of(
                "model", chatbotModel,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        try {
            Map<String, Object> response = vllmChat.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            e.printStackTrace();
            return "죄송합니다. 일시적인 오류가 발생했습니다.";
        }
    }

    // 외부 호출용
    public List<Chat> getSessionHistory(String sessionId) {
        return chatRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public List<Chat> getUserRecentChats(Long userIdx, int limit) {
        return chatRepository.findByUserIdxOrderByCreatedAtDesc(userIdx)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void rateChat(Long chatIdx, Byte rating, String feedback) {
        Chat chat = chatRepository.findById(chatIdx)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        chat.setRating(rating);
        chat.setFeedback(feedback);
        chatRepository.save(chat);
    }

    /**
     * 세션 삭제
     */
    @Transactional
    public void deleteSession(String sessionId) {
        chatRepository.deleteBySessionId(sessionId);
    }

    /**
     * 사용자 채팅 통계
     */
    public ChatController.ChatStatsDto getUserChatStats(Long userIdx) {
        List<Chat> allChats = chatRepository.findByUserIdx(userIdx);

        int totalChats = allChats.size();

        int avgResponseTime = (int) allChats.stream()
                .filter(c -> c.getResponseTimeMs() != null)
                .mapToInt(Chat::getResponseTimeMs)
                .average()
                .orElse(0);

        double avgRating = allChats.stream()
                .filter(c -> c.getRating() != null)
                .mapToInt(Chat::getRating)
                .average()
                .orElse(0.0);

        return new ChatController.ChatStatsDto(totalChats, avgResponseTime, avgRating);
    }
}
