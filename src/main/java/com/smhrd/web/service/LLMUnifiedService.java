package com.smhrd.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM 통합 서비스
 *
 * 1) RAW 마크다운 실행: DB 프롬프트(제목) + 원문을 그대로 LLM에 먹여 마크다운 전문(String)을 반환
 * 2) 요약 JSON 모드: summary + keywords(5) + category(대/중/소) + tags JSON을 받아 자동 분류/태깅에 활용
 * 3) 챗봇 간단 응답: 컨텍스트 프롬프트를 그대로 전달해 짧은 답 생성
 * 4) 로컬 카테고리 매칭/폴더 생성 보조 메서드 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LLMUnifiedService {

    // ====== Infra ======
    private final WebClient vllmWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== Domain Repositories ======
    private final PromptRepository promptRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final NoteFolderRepository noteFolderRepository;
    private final TagRepository tagRepository;
    private final NoteTagRepository noteTagRepository;
    private final CategoryHierarchyRepository categoryHierarchyRepository;
    private final TestSummaryRepository testSummaryRepository;

    // ====== LLM Settings ======
    @Value("${vllm.api.model}")
    private String modelName;

    @Value("${vllm.api.max-tokens}")
    private int maxTokens;

    @Value("${vllm.api.temperature}")
    private double temperature;

    @Value("${vllm.api.context-limit:30000}")
    private int contextLimit;

    private String fixFences(String md) {
        if (md == null) return "";
        String s = md.trim();
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf("```", idx)) != -1) {
            count++;
            idx += 3;
        }
        if ((count % 2) != 0) {
            s += "\n```";
        }
        return s;
    }

    // =====================================================================
    //  A. RAW 마크다운 실행
    // =====================================================================
    public String runPromptMarkdown(long userIdx, String promptTitle, String original) throws Exception {
        userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

        String promptText = promptRepository.findByTitle(promptTitle)
                .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
                .getContent();

        String fullPrompt = promptText + "\n\n" + (original == null ? "" : original);
        int safeMax = computeSafeMaxTokens(fullPrompt);

        Map<String, Object> chatReq = new HashMap<>();
        chatReq.put("model", modelName);
        chatReq.put("max_tokens", safeMax);
        chatReq.put("temperature", temperature);
        chatReq.put("messages", List.of(
                Map.of("role", "user", "content", fullPrompt)
        ));

        try {
            String resp = vllmWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(chatReq)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (resp != null && resp.trim().startsWith("#")) {
                log.warn("🔖 vLLM 로그 응답 감지, 원본 그대로 반환");
                return resp;
            }

            String md = extractAnyContent(resp);
            return fixFences(md);
        } catch (Exception chatFail) {
            log.warn("chat.completions 실패, text로 폴백: {}", chatFail.getMessage());

            Map<String, Object> textReq = new HashMap<>();
            textReq.put("model", modelName);
            textReq.put("max_tokens", safeMax);
            textReq.put("temperature", temperature);
            textReq.put("prompt", fullPrompt);

            String resp = vllmWebClient.post()
                    .uri("/v1/completions")
                    .bodyValue(textReq)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String md = extractAnyContent(resp);
            return fixFences(md);
        }
    }

    // =====================================================================
    //  B. 요약 JSON 모드
    // =====================================================================
    public UnifiedResult summarizeText(long userIdx, String content, String promptTitle) {
        userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

        String promptText = promptRepository.findByTitle(promptTitle)
                .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
                .getContent();

        return callUnifiedLLM(promptText, trimForTokens(content));
    }

    public UnifiedResult summarizeFile(long userIdx, MultipartFile file, String promptTitle) {
        userRepository.findByUserIdx(userIdx)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userIdx));

        String promptText = promptRepository.findByTitle(promptTitle)
                .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
                .getContent();

        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            return callUnifiedLLM(promptText, trimForTokens(text));
        } catch (Exception e) {
            throw new RuntimeException("파일 텍스트 변환 실패: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    //  C. Chat 전용
    // =====================================================================
    public String generateResponse(String contextualPrompt) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("model", modelName);
            req.put("max_tokens", Math.min(maxTokens, 800));
            req.put("temperature", 1.0);
            req.put("stream", false);

            String system = "You are a helpful assistant. Answer in Korean";
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", contextualPrompt)
            );
            req.put("messages", messages);

            String response = vllmWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractAnyContent(response);
        } catch (Exception e) {
            return "잠시 후 다시 시도해주세요. (" + e.getMessage() + ")";
        }
    }

    // =====================================================================
    //  D. LLM 호출 (Unified JSON)
    // =====================================================================
    private UnifiedResult callUnifiedLLM(String promptText, String original) {
        try {
            String fullPrompt =
                    promptText + "\n\n" + original + "\n\n" +
                            "아래 JSON 스키마로만 출력하세요. 추가 텍스트/설명은 절대 금지.\n" +
                            "{\n" +
                            "  \"summary\": string,\n" +
                            "  \"keywords\": string[5],\n" +
                            "  \"category\": { \"large\": string, \"medium\": string, \"small\": string },\n" +
                            "  \"tags\": string[]\n" +
                            "}\n";

            int safeMax = computeSafeMaxTokens(fullPrompt);

            Map<String, Object> chatReq = new HashMap<>();
            chatReq.put("model", modelName);
            chatReq.put("max_tokens", safeMax);
            chatReq.put("temperature", temperature);
            chatReq.put("messages", List.of(
                    Map.of("role", "user", "content", fullPrompt)
            ));

            String response;
            String jsonText;

            try {
                response = vllmWebClient.post()
                        .uri("/v1/chat/completions")
                        .bodyValue(chatReq)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                jsonText = extractAnyContent(response);
            } catch (RuntimeException chatErr) {
                log.warn("chat.completions 실패: {}", chatErr.getMessage());

                Map<String, Object> textReq = new HashMap<>();
                textReq.put("model", modelName);
                textReq.put("max_tokens", safeMax);
                textReq.put("temperature", temperature);
                textReq.put("prompt", fullPrompt);

                response = vllmWebClient.post()
                        .uri("/v1/completions")
                        .bodyValue(textReq)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                jsonText = extractAnyContent(response);
            }

            jsonText = stripFence(jsonText);

            JsonNode root = objectMapper.readTree(jsonText);
            UnifiedResult r = new UnifiedResult();
            r.setSummary(root.path("summary").asText(""));
            r.setKeywords(readArray(root.path("keywords")));

            JsonNode cat = root.path("category");
            r.setCategory(new CategoryPath(
                    cat.path("large").asText("기타"),
                    cat.path("medium").asText("미분류"),
                    cat.path("small").asText("일반")
            ));
            r.setTags(readArray(root.path("tags")));
            return r;

        } catch (Exception e) {
            throw new RuntimeException("LLM 통합 호출 실패: " + e.getMessage(), e);
        }
    }

    private String extractAnyContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            String c = choices.get(0).path("message").path("content").asText(null);
            if (c != null && !c.isBlank()) return c;

            c = choices.get(0).path("text").asText(null);
            if (c != null && !c.isBlank()) return c;
        }
        String out = root.path("output_text").asText(null);
        if (out != null && !out.isBlank()) return out;
        return response;
    }

    private String stripFence(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first > 0 && last > first) {
                return s.substring(first + 1, last).trim();
            }
        }
        return s;
    }

    private List<String> readArray(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText("")));
        }
        return list;
    }

    private int computeSafeMaxTokens(String fullPrompt) {
        int inputTok = estimateTokens(fullPrompt);
        int buffer   = Math.max(256, (int)(contextLimit * 0.1));
        int avail    = Math.max(0, contextLimit - inputTok - buffer);
        int safe     = Math.max(256, Math.min(maxTokens, avail));
        log.info("[LLM] ctxLimit={}, input≈{}, buffer={}, safeMax={}", contextLimit, inputTok, buffer, safe);
        return safe;
    }

    private int estimateTokens(String s) {
        if (s == null || s.isBlank()) return 0;
        return (int) Math.ceil(s.length() / 3.0);
    }

    private String trimForTokens(String content) {
        if (content == null) return "";
        int MAX_CHARS = 4000;
        return content.length() > MAX_CHARS
                ? content.substring(0, MAX_CHARS) + "\n\n...(truncated)"
                : content;
    }

    // =====================================================================
    //  F. Category 매칭 & 폴더 경로
    // =====================================================================
    public CategoryPath matchCategory(List<String> keywords, CategoryPath llmCategory) {
        if (keywords == null) keywords = List.of();
        Set<String> keyset = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<CategoryHierarchy> all = categoryHierarchyRepository.findAll();
        int bestScore = -1;
        CategoryHierarchy best = null;

        for (CategoryHierarchy c : all) {
            int score = scoreCategory(keyset, c.getKeywords());
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        if (best != null && bestScore >= 20) {
            return new CategoryPath(best.getLargeCategory(), best.getMediumCategory(), best.getSmallCategory());
        }
        return llmCategory != null ? llmCategory : new CategoryPath("기타", "미분류", "일반");
    }

    public Long ensureNoteFolderPath(long userIdx, CategoryPath path) {
        if (path == null) return null;
        Long parentId = null;
        parentId = findOrCreate(userIdx, parentId, path.getLarge());
        parentId = findOrCreate(userIdx, parentId, path.getMedium());
        parentId = findOrCreate(userIdx, parentId, path.getSmall());
        return parentId;
    }

    private Long findOrCreate(long userIdx, Long parentId, String name) {
        if (name == null || name.isBlank()) return parentId;

        List<NoteFolder> siblings = (parentId == null)
                ? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userIdx)
                : noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentId);

        Optional<NoteFolder> found = siblings.stream()
                .filter(f -> f.getFolderName().equals(name))
                .findFirst();
        if (found.isPresent()) return found.get().getFolderId();

        NoteFolder folder = NoteFolder.builder()
                .userIdx(userIdx)
                .folderName(name)
                .parentFolderId(parentId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return noteFolderRepository.save(folder).getFolderId();
    }

    private int scoreCategory(Set<String> keys, String categoryKeywordsCsv) {
        if (categoryKeywordsCsv == null || categoryKeywordsCsv.isBlank()) return 0;
        Set<String> cat = Arrays.stream(categoryKeywordsCsv.toLowerCase().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        int score = 0;
        for (String k : keys) {
            for (String ck : cat) {
                if (k.contains(ck) || ck.contains(k)) score += 10;
                else if (isSimilar(k, ck)) score += 5;
            }
        }
        return score;
    }

    private boolean isSimilar(String a, String b) {
        if (a.length() < 3 || b.length() < 3) return false;
        int d = editDistance(a, b);
        int m = Math.max(a.length(), b.length());
        return (double) d / m < 0.3;
    }

    private int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else if (a.charAt(i - 1) == b.charAt(j - 1)) dp[i][j] = dp[i - 1][j - 1];
                else dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
            }
        }
        return dp[a.length()][b.length()];
    }

    // =====================================================================
    //  G. DTOs
    // =====================================================================
    @Data
    public static class UnifiedResult {
        private String summary;
        private List<String> keywords;
        private CategoryPath category;
        private List<String> tags;
    }

    @Data
    public static class CategoryPath {
        private final String large;
        private final String medium;
        private final String small;
    }

    // -----------------------------------------------------
    // ✅ NotionController 호환용: 텍스트 3인자 버전
    // -----------------------------------------------------
    public TestSummary processText(long userIdx, String content, String promptTitle) {
        long start = System.currentTimeMillis();
        try {
            String markdown = runPromptMarkdown(userIdx, promptTitle, content);
            return TestSummary.builder()
                    .testType("TEXT")
                    .promptTitle(promptTitle)
                    .originalContent(content)
                    .aiSummary(markdown)
                    .status("SUCCESS")
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return TestSummary.builder()
                    .testType("TEXT")
                    .promptTitle(promptTitle)
                    .originalContent(content)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    public TestSummary processText(long userIdx, String content) {
        return processText(userIdx, content, "심플버전");
    }

    // -----------------------------------------------------
    // ✅ NotionController 호환용: 파일 3인자 버전
    // -----------------------------------------------------
    public TestSummary processFile(long userIdx, MultipartFile file, String promptTitle) {
        long start = System.currentTimeMillis();
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String markdown = runPromptMarkdown(userIdx, promptTitle, text);
            return TestSummary.builder()
                    .testType("FILE")
                    .promptTitle(promptTitle)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .aiSummary(markdown)
                    .status("SUCCESS")
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return TestSummary.builder()
                    .testType("FILE")
                    .promptTitle(promptTitle)
                    .fileName(file != null ? file.getOriginalFilename() : null)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    public TestSummary processFile(long userIdx, String promptTitle, MultipartFile file) {
        return processFile(userIdx, file, promptTitle);
    }

}
