// src/main/java/com/smhrd/web/service/LLMTestService.java
package com.smhrd.web.service;

import com.smhrd.web.entity.Prompt;
import com.smhrd.web.entity.TestSummary;
import com.smhrd.web.repository.PromptRepository;
import com.smhrd.web.repository.TestSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMTestService {

    public final TestSummaryRepository testSummaryRepository;
    private final PromptRepository promptRepository;
    private final VllmApiService vllmApiService;

    // ✅ 반드시 주입: 실제 파서
    private final FileParseService fileParseService;

    /** 텍스트 기반 LLM 테스트 */
    @Transactional
    public TestSummary processTextTest(String content, String promptTitle) {
        long t0 = System.currentTimeMillis();
        TestSummary ts = TestSummary.builder()
                .testType("TEXT")
                .promptTitle(promptTitle)
                .originalContent(content)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            String ai = vllmApiService.generateNotion(content, promptTitle);
            ts.setAiSummary(ai);
            ts.setStatus("SUCCESS");
        } catch (Exception e) {
            ts.setStatus("FAILED");
            ts.setErrorMessage(e.getMessage());
            log.error("TEXT 테스트 실패", e);
        }
        ts.setProcessingTimeMs(System.currentTimeMillis() - t0);
        return testSummaryRepository.save(ts);
    }

    /** 파일 기반 LLM 테스트 */
    @Transactional
    public TestSummary processFileTest(MultipartFile file, String promptTitle) {
        long t0 = System.currentTimeMillis();
        TestSummary ts = TestSummary.builder()
                .testType("FILE")
                .promptTitle(promptTitle)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .createdAt(LocalDateTime.now())
                .build();
        try {
            // ✅ 핵심: 여기서 반드시 FileParseService 사용
            String content = fileParseService.extractText(file);
            ts.setOriginalContent(content);

            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            String ai = vllmApiService.generateNotion(content, promptTitle);

            ts.setAiSummary(ai);
            ts.setStatus("SUCCESS");
        } catch (Exception e) {
            ts.setStatus("FAILED");
            ts.setErrorMessage(e.getMessage());
            log.error("FILE 테스트 실패", e);
        }
        ts.setProcessingTimeMs(System.currentTimeMillis() - t0);
        return testSummaryRepository.save(ts);
    }

    public List<TestSummary> getTestResults() {
        return testSummaryRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public List<com.smhrd.web.entity.Prompt> getAvailablePrompts() {
        return promptRepository.findAll();
    }
}
