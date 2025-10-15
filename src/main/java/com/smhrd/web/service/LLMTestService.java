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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMTestService {

    public final TestSummaryRepository testSummaryRepository;
    private final PromptRepository promptRepository;
    private final VllmApiService vllmApiService;

    /**
     * 텍스트 기반 LLM 테스트
     */
    @Transactional
    public TestSummary processTextTest(String content, String promptTitle) {
        log.info("=== 텍스트 기반 LLM 테스트 시작 ===");
        log.info("입력 텍스트 길이: {}", content.length());
        log.info("사용 프롬프트: {}", promptTitle);

        long startTime = System.currentTimeMillis();
        TestSummary testSummary = TestSummary.builder()
                .testType("TEXT")
                .promptTitle(promptTitle)
                .originalContent(content)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            // 1. 프롬프트 조회
            log.info("Step 1: 프롬프트 조회 시작");
            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            log.info("Step 1: 프롬프트 조회 성공 - ID: {}, 내용 길이: {}",
                    prompt.getPromptId(), prompt.getContent().length());

            // 2. VLLM API 호출
            log.info("Step 2: VLLM API 호출 시작");
            String aiResponse = vllmApiService.generateNotion(content, promptTitle);
            log.info("Step 2: VLLM API 호출 성공 - 응답 길이: {}", aiResponse.length());

            // 3. 결과 저장
            long processingTime = System.currentTimeMillis() - startTime;
            testSummary.setAiSummary(aiResponse);
            testSummary.setProcessingTimeMs(processingTime);
            testSummary.setStatus("SUCCESS");

            log.info("Step 3: 처리 완료 - 소요시간: {}ms", processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            testSummary.setProcessingTimeMs(processingTime);
            testSummary.setStatus("FAILED");
            testSummary.setErrorMessage(e.getMessage());

            log.error("❌ 텍스트 처리 실패 - 소요시간: {}ms", processingTime, e);
        }

        TestSummary savedTest = testSummaryRepository.save(testSummary);
        log.info("=== 텍스트 기반 LLM 테스트 완료 - 테스트 ID: {} ===", savedTest.getTestId());

        return savedTest;
    }

    /**
     * 파일 기반 LLM 테스트
     */
    @Transactional
    public TestSummary processFileTest(MultipartFile file, String promptTitle) {
        log.info("=== 파일 기반 LLM 테스트 시작 ===");
        log.info("파일명: {}, 크기: {} bytes", file.getOriginalFilename(), file.getSize());
        log.info("사용 프롬프트: {}", promptTitle);

        long startTime = System.currentTimeMillis();
        TestSummary testSummary = TestSummary.builder()
                .testType("FILE")
                .promptTitle(promptTitle)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .createdAt(LocalDateTime.now())
                .build();

        try {
            // 1. 파일 내용 추출
            log.info("Step 1: 파일 내용 추출 시작");
            String fileContent = extractFileContent(file);
            testSummary.setOriginalContent(fileContent);
            log.info("Step 1: 파일 내용 추출 성공 - 추출된 텍스트 길이: {}", fileContent.length());

            // 2. 프롬프트 조회
            log.info("Step 2: 프롬프트 조회 시작");
            Prompt prompt = promptRepository.findByTitle(promptTitle)
                    .orElseThrow(() -> new RuntimeException("프롬프트를 찾을 수 없습니다: " + promptTitle));
            log.info("Step 2: 프롬프트 조회 성공 - ID: {}", prompt.getPromptId());

            // 3. VLLM API 호출
            log.info("Step 3: VLLM API 호출 시작");
            String aiResponse = vllmApiService.generateNotion(fileContent, promptTitle);
            log.info("Step 3: VLLM API 호출 성공 - 응답 길이: {}", aiResponse.length());

            // 4. 결과 저장
            long processingTime = System.currentTimeMillis() - startTime;
            testSummary.setAiSummary(aiResponse);
            testSummary.setProcessingTimeMs(processingTime);
            testSummary.setStatus("SUCCESS");

            log.info("Step 4: 처리 완료 - 소요시간: {}ms", processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            testSummary.setProcessingTimeMs(processingTime);
            testSummary.setStatus("FAILED");
            testSummary.setErrorMessage(e.getMessage());

            log.error("❌ 파일 처리 실패 - 소요시간: {}ms", processingTime, e);
        }

        TestSummary savedTest = testSummaryRepository.save(testSummary);
        log.info("=== 파일 기반 LLM 테스트 완료 - 테스트 ID: {} ===", savedTest.getTestId());

        return savedTest;
    }

    /**
     * 파일 내용 추출
     */
    private String extractFileContent(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        log.info("파일 확장자: {}", extension);

        try {
            switch (extension) {
                case "txt":
                case "md":
                    String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                    log.info("텍스트 파일 읽기 성공");
                    return content;

                default:
                    // 기본적으로 텍스트로 읽기 시도
                    String defaultContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    log.info("기본 텍스트로 읽기 성공");
                    return defaultContent;
            }
        } catch (Exception e) {
            log.error("파일 내용 추출 실패", e);
            throw new IOException("파일 내용을 추출할 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 테스트 결과 목록 조회
     */
    public List<TestSummary> getTestResults() {
        log.info("테스트 결과 목록 조회");
        return testSummaryRepository.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * 사용 가능한 프롬프트 목록 조회
     */
    public List<Prompt> getAvailablePrompts() {
        log.info("사용 가능한 프롬프트 목록 조회");
        List<Prompt> prompts = promptRepository.findAll();
        log.info("조회된 프롬프트 개수: {}", prompts.size());
        return prompts;
    }
}
