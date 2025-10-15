package com.smhrd.web.service;

import com.smhrd.web.controller.ChatController.ChatResponse;
import com.smhrd.web.entity.Chat;
import com.smhrd.web.entity.User;
import com.smhrd.web.repository.ChatRepository;
import com.smhrd.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final VllmApiService vllmApiService;
    private final UserRepository userRepository;

    public ChatResponse processChat(Long userIdx, String message) {
        // 1. 기존 대화 이력 조회 (키워드 매칭)
        List<Chat> relevantHistory = findRelevantHistory(userIdx, message);

        // 2. 컨텍스트 구성
        String contextualPrompt = buildContextualPrompt(message, relevantHistory);

        // 3. LLM 호출
        String aiResponse = vllmApiService.generateResponse(contextualPrompt);

        // 4. 대화 저장
        saveConversation(userIdx, message, aiResponse);

        return new ChatResponse(aiResponse, relevantHistory.size());
    }

    private List<Chat> findRelevantHistory(Long userIdx, String message) {
        String keyword = extractKeyword(message);
        // userIdx 기반으로 대화 조회
        return chatRepository.findByUser_UserIdxOrderByCreatedAtDesc(userIdx).stream()
                .filter(c ->
                        c.getQuestion().toLowerCase().contains(keyword) ||
                                c.getAnswer().toLowerCase().contains(keyword))
                .limit(5)  // 최근 관련 대화 5개만
                .collect(Collectors.toList());
    }

    public List<Chat> getUserChatHistory(Long userIdx) {
        return chatRepository.findByUser_UserIdxOrderByCreatedAtDesc(userIdx);
    }

    private String buildContextualPrompt(String message, List<Chat> history) {
        StringBuilder sb = new StringBuilder();
        for (Chat c : history) {
            sb.append("Q: ").append(c.getQuestion()).append("\n")
                    .append("A: ").append(c.getAnswer()).append("\n\n");
        }
        sb.append("User: ").append(message);
        return sb.toString();
    }

    private void saveConversation(Long userIdx, String question, String answer) {
        User user = userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user"));
        Chat chat = Chat.builder()
                .user(user)
                .question(question)
                .answer(answer)
                .build();
        chatRepository.save(chat);
    }

    private String extractKeyword(String message) {
        String[] parts = message.trim().split("\\s+");
        return parts.length > 0 ? parts[0].toLowerCase() : "";
    }
}
