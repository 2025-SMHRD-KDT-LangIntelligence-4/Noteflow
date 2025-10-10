package com.smhrd.web.service;

import com.smhrd.web.entity.Prompt;
import com.smhrd.web.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptService {
    private final PromptRepository promptRepository;

    public String getInstruction(String key) {
        return promptRepository.findByTitle(key)
                .map(Prompt::getContent)
                .orElse("기본 AI 어시스턴트입니다. 도움이 필요하면 언제든 말씀해 주세요.");
    }
}
