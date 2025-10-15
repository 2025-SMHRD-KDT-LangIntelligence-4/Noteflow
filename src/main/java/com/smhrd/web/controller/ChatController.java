package com.smhrd.web.controller;

import com.smhrd.web.entity.Chat;
import com.smhrd.web.service.ChatService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ChatResponse sendMessage(@RequestBody ChatRequest request, Authentication auth) {
        // Authentication에서 CustomUserDetails로 캐스팅 후 user_idx 가져오기
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();

        return chatService.processChat(userIdx, request.getMessage());
    }

    @GetMapping("/history")
    public List<Chat> getChatHistory(Authentication auth) {
        Long userIdx = ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();

        return chatService.getUserChatHistory(userIdx);
    }

    @Getter
    @Setter
    public static class ChatRequest {
        private String message;
    }

    @Getter
    @AllArgsConstructor
    public static class ChatResponse {
        private final String reply;
        private final int historyCount;
    }
}
