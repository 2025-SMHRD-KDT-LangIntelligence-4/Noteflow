package com.smhrd.web.service;

import com.smhrd.web.controller.ChatController;
import com.smhrd.web.controller.ChatController.ChatResponse;
import com.smhrd.web.entity.Chat;
import com.smhrd.web.repository.ChatRepository;
import com.smhrd.web.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    @Qualifier("vllmChat")
    private final WebClient vllmChat;

    @Qualifier("embeddingClient")
    private final WebClient embeddingClient;

    private final ChatRepository chatRepository;
    private final TestResultRepository testResultRepository;

    @Autowired
    private PostgresVectorService postgresVectorService;

    @Autowired
    private ChatbotLectureService chatbotLectureService;

    @Value("${vllm.chatbot.model}")
    private String chatbotModel;

    @Value("${vllm.chatbot.max-tokens}")
    private Integer maxTokens;

    @Value("${vllm.chatbot.temperature}")
    private Double temperature;

    @Value("${vllm.chatbot.context-limit}")
    private Integer contextLimit;

    /**
     * ✅ 메인 챗 처리 로직
     */
    public ChatResponse processChat(Long userIdx, String message, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        long startTime = System.currentTimeMillis();
        try {
            log.info("💬 사용자 메시지: {}", message);

            // LLM 기반 인텐트 분류
            String detectedIntent = classifyIntent(message);
            log.info("🎯 감지된 인텐트: {}", detectedIntent);

            // 인텐트별 라우팅
            String botReply = routeByIntent(userIdx, message, detectedIntent, sessionId);

            return saveAndReturnChat(userIdx, sessionId, message, botReply, startTime);

        } catch (Exception e) {
            log.error("❌ 챗봇 처리 중 오류", e);
            return new ChatResponse("죄송합니다. 처리 중 오류가 발생했습니다.", 0, sessionId);
        }
    }

    /**
     * ✅ LLM 기반 인텐트 분류
     */
    private String classifyIntent(String message) {
        try {
            String systemPrompt = """
사용자 의도를 아래 중 하나로 분류하세요.

입력: "%s"

분류:
NOTE_COUNT, NOTE_LIST, NOTE_SEARCH
SCHEDULE_CREATE, SCHEDULE_LIST, SCHEDULE_SEARCH
LECTURE_RECOMMEND, LECTURE_SEARCH, LECTURE_LIST
EXAM_CREATE, EXAM_STATS, EXAM_HISTORY
HELP, GENERAL_CHAT

규칙:
- "강의 추천", "~강의 알려줘" → LECTURE_RECOMMEND
- "~강의 찾아줘", "~수업" → LECTURE_SEARCH
- 명확한 키워드 없으면 → GENERAL_CHAT

답변 형식: 인텐트명만 반환 (예: LECTURE_RECOMMEND)
""".formatted(message);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", message));

            Map<String, Object> request = Map.of(
                    "model", chatbotModel,
                    "messages", messages,
                    "max_tokens", 50,
                    "temperature", 0.3
            );

            Map<String, Object> response = vllmChat.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) return "GENERAL_CHAT";

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
            String rawIntent = ((String) messageObj.get("content")).trim().toUpperCase();

            log.info("🎯 LLM 원본 응답: {}", rawIntent);
            return extractIntent(rawIntent);

        } catch (Exception e) {
            log.error("❌ 인텐트 분류 실패", e);
            return "GENERAL_CHAT";
        }
    }

    private String extractIntent(String rawResponse) {
        if (rawResponse.contains("NOTE_COUNT")) return "NOTE_COUNT";
        if (rawResponse.contains("NOTE_LIST")) return "NOTE_LIST";
        if (rawResponse.contains("NOTE_SEARCH")) return "NOTE_SEARCH";

        if (rawResponse.contains("SCHEDULE_CREATE")) return "SCHEDULE_CREATE";
        if (rawResponse.contains("SCHEDULE_LIST")) return "SCHEDULE_LIST";
        if (rawResponse.contains("SCHEDULE_SEARCH")) return "SCHEDULE_SEARCH";

        if (rawResponse.contains("LECTURE_RECOMMEND")) return "LECTURE_RECOMMEND";
        if (rawResponse.contains("LECTURE_SEARCH")) return "LECTURE_SEARCH";
        if (rawResponse.contains("LECTURE_LIST")) return "LECTURE_LIST";

        if (rawResponse.contains("EXAM_CREATE")) return "EXAM_CREATE";
        if (rawResponse.contains("EXAM_STATS")) return "EXAM_STATS";
        if (rawResponse.contains("EXAM_HISTORY")) return "EXAM_HISTORY";

        if (rawResponse.contains("HELP")) return "HELP";

        return "GENERAL_CHAT";
    }

    /**
     * ✅ 인텐트별 라우팅
     */
    private String routeByIntent(Long userIdx, String message, String intent, String sessionId) {
        switch (intent) {
            case "NOTE_COUNT":
                return handleCountNotes(userIdx);
            case "NOTE_LIST":
                // ✅ 추가: 검색어 있으면 Vector 검색
                if (message.contains("파이썬") || message.contains("자바") ||
                        !message.matches(".*목록.*|.*리스트.*|.*보여줘.*")) {
                    return handleSearchNotes(userIdx, message, sessionId);
                }
                return handleListNotes(userIdx);
            case "NOTE_SEARCH":
                return handleSearchNotes(userIdx, message, sessionId);
            case "COUNT_EXAMS":
                return handleCountExams(userIdx);
            case "EXAM_STATS":
                return handleExamStats(userIdx);

            // 강의 관련 추가
            case "LECTURE_RECOMMEND":
                return handleRecommendLecture(message);
            case "LECTURE_SEARCH":  // ⬅️ 이거 없었음
                return handleLectureSearch(message);
            case "LECTURE_LIST":    // ⬅️ 이것도 없었음
                return handleLectureList(userIdx);

            case "GENERAL_CHAT":
            default:
                return handleGeneralChat(userIdx, message, sessionId);
        }
    }

    private String handleLectureSearch(String message) {
        try {
            Map<String, Object> parsed = chatbotLectureService.parseChatbotQuery(message);
            String keyword = (String) parsed.get("keyword");

            Map<String, Object> lectureResult = chatbotLectureService.searchLecturesForChat(
                    keyword,
                    (List<String>) parsed.get("tags"),
                    (String) parsed.getOrDefault("searchMode", "OR"),
                    null
            );

            if ((Integer) lectureResult.get("count") == 0) {
                return "죄송합니다. 해당 강의를 찾을 수 없습니다. 🔍";
            }

            // ✅ 원래대로: 강의 목록 표시 + URL
            StringBuilder result = new StringBuilder("🎓 **추천 강의**\n\n");
            List<Map<String, Object>> lectures = (List<Map<String, Object>>) lectureResult.get("lectures");

            for (int i = 0; i < Math.min(3, lectures.size()); i++) {
                Map<String, Object> lec = lectures.get(i);
                String title = (String) lec.get("lec_title");
                String url = (String) lec.get("lec_url");

                result.append(String.format("%d. **%s**\n", i + 1, title));
                result.append(String.format("   🔗 [강의 바로가기](%s)\n\n", url));
            }

            if (lectures.size() > 3) {
                result.append(String.format("*외 %d개 강의 더 있음*", lectures.size() - 3));
            }

            return result.toString();

        } catch (Exception e) {
            log.error("강의 검색 실패", e);
            return "죄송합니다. 강의 검색 중 오류가 발생했습니다.";
        }
    }

    private String handleLectureList(Long userIdx) {
        // 사용자가 수강중인 강의 목록 (구현 필요)
        return "수강중인 강의 목록 기능은 준비 중입니다. 📚";
    }

    private String handleCountNotes(Long userIdx) {
        try {
            long totalNotes = postgresVectorService.countUserNotes(userIdx);
            return String.format("📝 현재 저장된 노트는 총 **%d개**입니다.", totalNotes);
        } catch (Exception e) {
            log.error("노트 개수 조회 실패", e);
            return "노트 개수를 조회할 수 없습니다.";
        }
    }

    private String handleListNotes(Long userIdx) {
        try {
            List<Map<String, Object>> allUserNotes = postgresVectorService.getRecentNotes(userIdx, 20);
            if (allUserNotes.isEmpty()) {
                return "아직 작성된 노트가 없습니다. 📝";
            }

            StringBuilder result = new StringBuilder("📚 **최근 작성한 노트**\n\n");
            for (int i = 0; i < Math.min(5, allUserNotes.size()); i++) {
                Map<String, Object> note = allUserNotes.get(i);
                String title = (String) note.get("title");

                if (title.length() > 50) {
                    title = title.substring(0, 50) + "...";
                }

                result.append(String.format("**%d.** %s\n", i + 1, title));
            }

            if (allUserNotes.size() > 5) {
                result.append(String.format("\n*... 외 %d개 노트 더 있음*\n", allUserNotes.size() - 5));
            }

            result.append("\n[FORM:GET:/notion/manage::전체 노트 보기]");
            return result.toString();

        } catch (Exception e) {
            log.error("노트 목록 조회 실패", e);
            return "죄송합니다. 노트 목록을 불러올 수 없습니다.";
        }
    }


    private String handleSearchNotes(Long userIdx, String message, String sessionId) {
        try {
            log.info("🔍 노트 내용 검색 시작: {}", message);

            // "마지막", "최근" 키워드 감지
            if (message.contains("마지막") || message.contains("최근") || message.contains("내용")) {
                List<Map<String, Object>> recentNotes = postgresVectorService.getRecentNotes(userIdx, 1);

                if (recentNotes.isEmpty()) {
                    return "아직 작성된 노트가 없습니다. 📝";
                }

                Map<String, Object> lastNote = recentNotes.get(0);
                String title = (String) lastNote.get("title");
                String content = (String) lastNote.get("content");

                if (title.length() > 50) {
                    title = title.substring(0, 50) + "...";
                }

                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }

                return String.format("""
                📝 **가장 최근 노트**
                
                **제목:** %s
                
                **내용:**
                %s
                
                [FORM:GET:/notion/manage::전체 노트 보기]
                """, title, content);
            }

            // ✅ 일반 키워드 검색
            log.info("🔍 Vector 검색 시작: {}", message);
            List<Float> queryVector = getEmbedding(message);
            log.info("✅ 임베딩 완료: {} 차원", queryVector.size());

            List<Map<String, Object>> relevantDocs = postgresVectorService.searchVectors(
                    userIdx, queryVector, null, null, 5);

            log.info("✅ Vector 검색 결과: {} 개", relevantDocs.size());

            if (relevantDocs.isEmpty()) {
                return "관련된 노트를 찾을 수 없습니다. 🔍";
            }

            // ✅ 제목만 보여주기
            StringBuilder result = new StringBuilder("🔍 **검색 결과**\n\n");
            result.append(String.format("총 **%d개**의 관련 노트를 찾았습니다.\n\n", relevantDocs.size()));

            for (int i = 0; i < Math.min(5, relevantDocs.size()); i++) {
                Map<String, Object> doc = relevantDocs.get(i);
                String title = (String) doc.get("title");

                if (title.length() > 50) {
                    title = title.substring(0, 50) + "...";
                }

                result.append(String.format("**%d.** %s\n", i + 1, title));
            }

            result.append("\n[FORM:GET:/notion/manage::전체 노트 보기]");
            return result.toString();

        } catch (Exception e) {
            log.error("노트 검색 실패", e);
            return "죄송합니다. 노트 검색 중 오류가 발생했습니다.";
        }
    }


    private String handleCountExams(Long userIdx) {
        try {
            long examCount = testResultRepository.countByUserUserIdx(userIdx);
            return String.format("📊 지금까지 **%d번**의 시험을 보셨습니다!", examCount);
        } catch (Exception e) {
            log.error("시험 횟수 조회 실패", e);
            return "죄송합니다. 시험 기록을 확인할 수 없습니다.";
        }
    }

    private String handleExamStats(Long userIdx) {
        try {
            long totalExams = testResultRepository.countByUserUserIdx(userIdx);
            long passedExams = testResultRepository.countByUserUserIdxAndPassedTrue(userIdx);
            Double avgScore = testResultRepository.findAverageScoreByUser(userIdx);

            if (totalExams == 0) {
                return "아직 시험 기록이 없습니다. 첫 시험에 도전해보세요! 💪";
            }

            double passRate = (double) passedExams / totalExams * 100;

            return String.format("""
                📊 **시험 통계**
                
                - 총 시험 횟수: %d회
                - 합격: %d회 (%.1f%%)
                - 평균 점수: %.1f점
                
                계속 화이팅하세요! 🎯
                """, totalExams, passedExams, passRate, avgScore != null ? avgScore : 0.0);

        } catch (Exception e) {
            log.error("시험 통계 조회 실패", e);
            return "죄송합니다. 통계를 확인할 수 없습니다.";
        }
    }

    private String handleRecommendLecture(String message) {
        try {
            Map<String, Object> parsed = chatbotLectureService.parseChatbotQuery(message);
            String keyword = (String) parsed.get("keyword");

            Map<String, Object> lectureResult = chatbotLectureService.searchLecturesForChat(
                    keyword,
                    (List<String>) parsed.get("tags"),
                    (String) parsed.getOrDefault("searchMode", "OR"),
                    null
            );

            if ((Integer) lectureResult.get("count") == 0) {
                return "추천할 수 있는 강의를 찾지 못했습니다. 😢";
            }

            // ✅ 수정: Form 방식으로 변경
            StringBuilder result = new StringBuilder("🎓 **추천 강의**\n\n");
            List<Map<String, Object>> lectures = (List<Map<String, Object>>) lectureResult.get("lectures");

            for (int i = 0; i < Math.min(3, lectures.size()); i++) {
                Map<String, Object> lec = lectures.get(i);
                result.append(String.format("**%d.** %s\n", i + 1, lec.get("lec_title")));
            }

            result.append(String.format("\n총 **%d개** 강의를 찾았습니다.\n\n", lectureResult.get("count")));
            result.append(String.format("[FORM:GET:/lecture/recommend:keywords=%s:강의목록 보기]", keyword));

            return result.toString();

        } catch (Exception e) {
            log.error("강의 추천 실패", e);
            return "죄송합니다. 강의 추천 중 오류가 발생했습니다.";
        }
    }


    private String handleGeneralChat(Long userIdx, String message, String sessionId) {
        try {
            List<Chat> chatHistory = getChatHistory(sessionId);
            List<Chat> recentHistory = chatHistory.stream()
                    .skip(Math.max(0, chatHistory.size() - 2))
                    .collect(Collectors.toList());

            String prompt = buildGeneralChatPrompt(message, recentHistory);
            return callChatbot(prompt, recentHistory);

        } catch (Exception e) {
            log.error("일반 대화 처리 실패", e);
            return "죄송합니다. 답변을 생성할 수 없습니다.";
        }
    }

    private String buildGeneralChatPrompt(String userQuestion, List<Chat> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("너는 친근하고 도움이 되는 학습 도우미 AI야.\n\n");
        prompt.append("⚠️ 중요 규칙:\n");
        prompt.append("1. 이전 대화와 무관하게 **현재 질문**에만 집중해서 답변해\n");
        prompt.append("2. 같은 답변을 반복하지 마\n");
        prompt.append("3. 사용자가 새로운 주제를 말하면 자연스럽게 전환해\n");
        prompt.append("4. 간단하고 자연스럽게 대화해\n\n");

        if (!history.isEmpty() && history.size() <= 2) {
            prompt.append("이전 대화 참고:\n");
            for (Chat chat : history) {
                String q = chat.getQuestion();
                String a = chat.getAnswer();
                if (q != null && q.length() > 50) q = q.substring(0, 50) + "...";
                if (a != null && a.length() > 50) a = a.substring(0, 50) + "...";
                prompt.append(String.format("사용자: %s\nAI: %s\n\n", q, a));
            }
        }

        prompt.append(String.format("현재 질문: %s\n\n답변:", userQuestion));
        return prompt.toString();
    }

    /**
     * ✅ DB 저장 및 응답 반환
     */
    private ChatResponse saveAndReturnChat(Long userIdx, String sessionId, String message,
                                           String botReply, long startTime) {
        int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

        Chat chat = new Chat();
        chat.setUserIdx(userIdx);
        chat.setSessionId(sessionId);
        chat.setQuestion(message);
        chat.setAnswer(botReply);
        chat.setResponseTimeMs(responseTimeMs);
        chatRepository.save(chat);

        int historyCount = chatRepository.countBySessionId(sessionId);
        log.info("✅ 채팅 저장 완료 - sessionId: {}", sessionId);

        return new ChatResponse(botReply, historyCount, sessionId);
    }

    // ===== 기존 메서드들 (변경 없음) =====

    private List<Float> getEmbedding(String text) {
        try {
            log.info("🔄 Embedding 생성 중: 텍스트 길이 = {}", text.length());

            Map<String, Object> response = embeddingClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("texts", List.of(text)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.error("❌ Embedding 응답 null");
                return new ArrayList<>();
            }

            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                log.error("❌ embeddings 배열이 null 또는 비어있음");
                return new ArrayList<>();
            }

            List<Float> result = embeddings.get(0).stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());

            log.info("✅ Embedding 생성 성공 - 차원: {}", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ Embedding 생성 실패", e);
            return new ArrayList<>();
        }
    }

    private List<Chat> getChatHistory(String sessionId) {
        List<Chat> allHistory = chatRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int total = allHistory.size();
        return allHistory.stream()
                .skip(Math.max(0, total - 5))
                .collect(Collectors.toList());
    }

    private String callChatbot(String prompt, List<Chat> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "너는 학습 도우미 AI야. 사용자가 공부한 내용을 기반으로 정확하고 친절하게 답변해줘."
        ));
        messages.add(Map.of("role", "user", "content", prompt));

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

            if (response == null) return "죄송합니다. 응답을 받을 수 없었습니다.";

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            log.error("❌ vLLM 호출 중 오류", e);
            return "죄송합니다. 일시적인 오류가 발생했습니다.";
        }
    }

    // ===== public 메서드들 =====

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

    @Transactional
    public void deleteSession(String sessionId) {
        chatRepository.deleteBySessionId(sessionId);
    }

    public ChatController.ChatStatsDto getUserChatStats(Long userIdx) {
        try {
            List<Chat> allChats = chatRepository.findByUserIdx(userIdx);

            if (allChats.isEmpty()) {
                return new ChatController.ChatStatsDto(0, 0, 0.0);
            }

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

        } catch (Exception e) {
            log.error("❌ 통계 조회 중 오류", e);
            return new ChatController.ChatStatsDto(0, 0, 0.0);
        }
    }
}
