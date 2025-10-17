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

@Transactional
public TestSummary processFileTestCustom(MultipartFile file, String customPrompt,
                                         Integer maxTokens, Double temperature) {
    long t0 = System.currentTimeMillis();
    TestSummary ts = TestSummary.builder()
        .testType("FILE")
        .promptTitle("커스텀")
        .fileName(file.getOriginalFilename())
        .fileSize(file.getSize())
        .originalPrompt(customPrompt)
        .createdAt(java.time.LocalDateTime.now())
        .build();
    try {
        String content = fileParseService.extractText(file); // 이미 존재하는 파서 사용 [1](https://o365scnu-my.sharepoint.com/personal/20142215_s_scnu_ac_kr/Documents/Microsoft%20Copilot%20Chat%20%ED%8C%8C%EC%9D%BC/%EC%A0%84%EC%B2%B4%EC%BD%94%EB%93%9C1017-1.txt)
        ts.setOriginalContent(content);
        String ai = vllmApiService.generateWithCustomSystem(customPrompt, content, maxTokens, temperature);
        ts.setAiSummary(ai);
        ts.setStatus("SUCCESS");
    } catch (Exception e) {
        ts.setStatus("FAILED");
        ts.setErrorMessage(e.getMessage());
        log.error("CUSTOM FILE 테스트 실패", e);
    }
    ts.setProcessingTimeMs(System.currentTimeMillis() - t0);
    return testSummaryRepository.save(ts);
}

@Transactional
public TestSummary processTextTestCustom(String content,
                                         String customPrompt,
                                         Integer maxTokens,
                                         Double temperature) {
    long t0 = System.currentTimeMillis();
    TestSummary ts = TestSummary.builder()
            .testType("TEXT")
            .promptTitle("커스텀")              // 표기용
            .originalContent(content)
            .originalPrompt(customPrompt)      // 커스텀 프롬프트 저장
            .createdAt(LocalDateTime.now())
            .build();
    try {
        String ai = vllmApiService.generateWithCustomSystem(
                customPrompt,
                content,
                maxTokens,
                temperature
        );
        ts.setAiSummary(ai);
        ts.setStatus("SUCCESS");
    } catch (Exception e) {
        log.error("CUSTOM TEXT 테스트 실패", e);
        ts.setStatus("FAILED");
        ts.setErrorMessage(e.getMessage());
    }
    ts.setProcessingTimeMs(System.currentTimeMillis() - t0);
    return testSummaryRepository.save(ts);
}

}



