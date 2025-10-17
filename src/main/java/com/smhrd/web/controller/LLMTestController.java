package com.smhrd.web.controller;

import com.smhrd.web.entity.Prompt;
import com.smhrd.web.entity.TestSummary;
import com.smhrd.web.repository.TestSummaryRepository;
import com.smhrd.web.service.LLMTestService;
import com.smhrd.web.service.NotionContentService;
import com.smhrd.web.service.VllmApiService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/test")
public class LLMTestController {

    private final LLMTestService llmTestService;
    private final Environment env;  // 환경 변수 주입
    private final NotionContentService notionContentService;
    private final VllmApiService vllmApiService;
    private final TestSummaryRepository testSummaryRepository;

    //  LLM 테스트 페이지 조회

    @GetMapping("/llm")
    public String showTestPage(Model model) {
        log.info("LLM 테스트 페이지 요청");

        try {
            List<Prompt> prompts = llmTestService.getAvailablePrompts();
            model.addAttribute("prompts", prompts);

            List<TestSummary> recentTests = llmTestService.getTestResults();
            model.addAttribute("recentTests", recentTests);

            log.info("데이터 로드 완료 - 프롬프트 수: {}, 최근 테스트 수: {}",
                    prompts.size(), recentTests.size());

        } catch (Exception e) {
            log.error("데이터 로드 실패", e);
            model.addAttribute("error", "데이터 로드에 실패했습니다: " + e.getMessage());
        }

        // vLLM 설정값 모델에 추가
        model.addAttribute("vllmBaseUrl",        env.getProperty("vllm.api.url"));
        model.addAttribute("vllmApiModel",       env.getProperty("vllm.api.model"));
        model.addAttribute("vllmApiMaxTokens",   Integer.parseInt(env.getProperty("vllm.api.max-tokens", "4000")));
        model.addAttribute("vllmApiTemperature", Double.parseDouble(env.getProperty("vllm.api.temperature", "0.7")));

        return "llm-test";
    }

    // 텍스트 기반 테스트


@PostMapping("/llm/text")
@ResponseBody
public ResponseEntity<Map<String, Object>> testText(@RequestBody TextTestRequest request) {
    log.info("텍스트 테스트 요청 - 모드: {}, 프롬프트: {}, 커스텀 길이: {}",
        request.getPromptMode(), request.getPromptTitle(),
        request.getCustomPrompt() == null ? 0 : request.getCustomPrompt().length());

    Map<String, Object> response = new HashMap<>();
    try {
        TestSummary result;
        if ("custom".equalsIgnoreCase(request.getPromptMode())) {
            // 커스텀 프롬프트 경로
            result = llmTestService.processTextTestCustom(
                request.getContent(),
                request.getCustomPrompt(),
                request.getMaxTokens(),
                request.getTemperature()
            );
        } else {
            // 기존 DB 프롬프트 경로
            result = llmTestService.processTextTest(
                request.getContent(),
                request.getPromptTitle()
            );
        }
        response.put("success", true);
        response.put("testId", result.getTestId());
        response.put("status", result.getStatus());
        response.put("processingTime", result.getProcessingTimeMs());
        response.put("summary", result.getAiSummary());
        response.put("error", result.getErrorMessage());
    } catch (Exception e) {
        log.error("텍스트 테스트 실패", e);
        response.put("success", false);
        response.put("message", "테스트 처리에 실패했습니다: " + e.getMessage());
    }
    return ResponseEntity.ok(response);
}

// 커스텀 전용

@Transactional
public TestSummary processTextTestCustom(String content, String customPrompt,
                                        Integer maxTokens, Double temperature) {
    long t0 = System.currentTimeMillis();
    TestSummary ts = TestSummary.builder()
        .testType("TEXT")
        .promptTitle("커스텀")             // 표기용
        .originalContent(content)
        .originalPrompt(customPrompt)      // 커스텀 프롬프트 저장
        .createdAt(java.time.LocalDateTime.now())
        .build();

    try {
        // vLLM 호출 (새 메서드 사용)
        String ai = vllmApiService.generateWithCustomSystem(
            customPrompt,              // system 프롬프트: 지시문
            content,                   // user 콘텐츠: 요약 대상
            maxTokens,                 // null이면 기본값
            temperature                // null이면 기본값
        );
        ts.setAiSummary(ai);
        ts.setStatus("SUCCESS");
    } catch (Exception e) {
        ts.setStatus("FAILED");
        ts.setErrorMessage(e.getMessage());
        log.error("CUSTOM TEXT 테스트 실패", e);
    }
    ts.setProcessingTimeMs(System.currentTimeMillis() - t0);
    return testSummaryRepository.save(ts);
}




    // 파일 기반 테스트 실행

    @PostMapping("/llm/file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("promptTitle") String promptTitle) {

        log.info("파일 테스트 요청 - 파일: {}, 크기: {} bytes, 프롬프트: {}",
                file.getOriginalFilename(), file.getSize(), promptTitle);

        Map<String, Object> response = new HashMap<>();

        try {
            // 파일 크기 제한 (10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
            }

            TestSummary result = llmTestService.processFileTest(file, promptTitle);

            response.put("success", true);
            response.put("testId", result.getTestId());
            response.put("status", result.getStatus());
            response.put("processingTime", result.getProcessingTimeMs());
            response.put("originalContent", result.getOriginalContent());
            response.put("summary", result.getAiSummary());
            response.put("error", result.getErrorMessage());

            log.info("파일 테스트 완료 - ID: {}, 상태: {}", result.getTestId(), result.getStatus());

        } catch (Exception e) {
            log.error("파일 테스트 실패", e);
            response.put("success", false);
            response.put("message", "파일 테스트 처리에 실패했습니다: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 테스트 결과 조회

    @GetMapping("/llm/result/{testId}")
    @ResponseBody
    public ResponseEntity<TestSummary> getTestResult(@PathVariable Long testId) {
        log.info("테스트 결과 조회 - ID: {}", testId);

        try {
            TestSummary result = llmTestService.testSummaryRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("테스트 결과를 찾을 수 없습니다."));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("테스트 결과 조회 실패 - ID: {}", testId, e);
            return ResponseEntity.notFound().build();
        }
    }
    @PostMapping("/save-note")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveNote(
            @RequestBody Map<String,String> req) {
        String title       = req.getOrDefault("title", "자동생성 노트");
        String summary     = req.getOrDefault("summary", "");
        Long   promptId    = Long.parseLong(req.getOrDefault("promptId", "0"));
        Long   userIdx     = Long.parseLong(req.getOrDefault("userIdx", "0"));

        Map<String,Object> res = new HashMap<>();
        try {
            Long noteId = notionContentService.saveNote(userIdx, title, summary, promptId);

            res.put("success", true);
            res.put("noteId", noteId);
        } catch (Exception e) {
            res.put("success", false);
            res.put("error", e.getMessage());
        }
        return ResponseEntity.ok(res);
    }
    

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class TextTestRequest {
	    private String content;         // 원문 텍스트
	    private String promptTitle;     // DB 프롬프트 제목
	    private String promptMode;      // "db" | "custom"
	    private String customPrompt;    // 커스텀 프롬프트(선택)
	    private Integer maxTokens;      // 선택: 토큰 상한
	    private Double temperature;     // 선택: 온도
	}

}
