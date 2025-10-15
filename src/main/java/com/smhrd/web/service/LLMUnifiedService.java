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
import com.smhrd.web.entity.TestSummary;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
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

    /** 서버/모델 컨텍스트 한도(예: 8192, 16384, 30000). 서버 환경에 맞게 설정 */
    @Value("${vllm.api.context-limit:30000}")
    private int contextLimit;

    // 클래스 LLMUnifiedService 내부(다른 private 메서드들 있는 섹션)에 추가
    private String fixFences(String md) {
        if (md == null) return "";
        String s = md.trim();
        // 문서 내  ``` (3백틱) 등장 횟수를 센 다음 홀수면 닫는 펜스를 하나 추가
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf("```", idx)) != -1) {
            count++;
            idx += 3;
        }
        if ((count % 2) != 0) {
            s += "\n```";  // 닫는 펜스 보정
        }
        return s;
    }
    // =====================================================================
    //  A. RAW 마크다운 실행 (DB 프롬프트 그대로)  ← 노션 생성 시 길고 구조화된 문서가 필요할 때
    // =====================================================================

    /**
     * DB 프롬프트(title) + 원문을 그대로 결합하여 LLM에 전달하고, 마크다운 전문(String)을 반환합니다.
     * chat 포맷 우선, 실패 시 text 포맷 폴백. 출력 토큰은 입력 길이에 따라 안전하게 보정합니다.
     */
    public String runPromptMarkdown(String userId, String promptTitle, String original) throws Exception {
        // 사용자 존재 확인
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));

        // 프롬프트 본문 조회
        String promptText = promptRepository.findByTitle(promptTitle)
                .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
                .getContent();

        // 결합 프롬프트
        String fullPrompt = promptText + "\n\n" + (original == null ? "" : original);
        int safeMax = computeSafeMaxTokens(fullPrompt);

        // 1) Chat 포맷 시도 (system에 'very concise' 같은 톤을 넣지 않음; DB 프롬프트가 모든 지시를 담당)
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
            // 응답이 '#'로 시작하면 로그와 함께 그대로 반환
            if (resp != null && resp.trim().startsWith("#")) {
                log.warn("🔖 vLLM 로그 응답 감지, 원본 그대로 반환");
                return resp;
            }

            String md = extractAnyContent(resp);
            return fixFences(md);
        } catch (Exception chatFail) {
            log.warn("chat.completions 실패, text로 폴백: {}", chatFail.getMessage());

            // 2) Text 포맷 폴백
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
    //  B. 요약 JSON 모드 (summary/keywords/category/tags)  ← 자동 분류/태깅 워크플로우에 사용
    // =====================================================================

    /** 텍스트 요약 + 키워드5 + 분류 + 태그 (노트 저장 없음) */
    public UnifiedResult summarizeText(String userId, String content, String promptTitle) {
        // 사용자 존재 체크
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));

        String promptText = promptRepository.findByTitle(promptTitle)
                .orElseThrow(() -> new IllegalArgumentException("프롬프트 없음: " + promptTitle))
                .getContent();

        // ⚠️ JSON 스키마 강제 모드: 자동 분류/태깅 파이프라인에 사용
        return callUnifiedLLM(promptText, trimForTokens(content));
    }

    /** 파일 요약 + 키워드5 + 분류 + 태그 (노트 저장 없음) */
    public UnifiedResult summarizeFile(String userId, MultipartFile file, String promptTitle) {
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + userId));

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
    //  C. Chat 전용: 간단 질의응답 (컨텍스트 프롬프트를 그대로 전달)
    // =====================================================================

    public String generateResponse(String contextualPrompt) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("model", modelName);
            req.put("max_tokens", Math.min(maxTokens, 800)); // 짧게
            req.put("temperature", 1.0);                    // 안정적 톤
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
    //  D. LLM 호출 (Unified JSON 모드) + 파서 보강 + 안전 토큰 처리
    // =====================================================================

    /**
     * 프롬프트 + 원문을 결합하여, "summary/keywords/category/tags" JSON만 출력하도록 LLM을 호출하고 파싱합니다.
     * chat 포맷 우선, 실패 시 text 포맷 폴백. 출력 토큰은 입력 길이에 따라 안전하게 보정합니다.
     */
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

            // 1) Chat 포맷
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

                // 2) Text 포맷 폴백
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

            // 코드펜스 제거(```json ... ```) 후 파싱
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

    /** Chat/Text 겸용 파서: message.content → choices[0].text → output_text 순으로 추출 */
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

    /** 문서 전체를 감싼 코드펜스 제거(내부 코드블록은 유지) */
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

    /** JSON 배열 노드 → List<String> */
    private List<String> readArray(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> list.add(n.asText("")));
        }
        return list;
    }

    // =====================================================================
    //  E. 안전 토큰 계산/입력 트림
    // =====================================================================

    /** 입력 길이에 따라 안전한 max_tokens 계산(컨텍스트 한도 고려) */
    private int computeSafeMaxTokens(String fullPrompt) {
        int inputTok = estimateTokens(fullPrompt);
        int buffer   = Math.max(256, (int)(contextLimit * 0.1)); // 10% 또는 최소 256
        int avail    = Math.max(0, contextLimit - inputTok - buffer);
        int safe     = Math.max(256, Math.min(maxTokens, avail)); // 최소 256 보장
        log.info("[LLM] ctxLimit={}, input≈{}, buffer={}, safeMax={}", contextLimit, inputTok, buffer, safe);
        return safe;
    }

    /** 아주 대략적인 토큰 추정: 1 토큰 ≈ 3 문자 (혼용 환경에서 보수적으로) */
    private int estimateTokens(String s) {
        if (s == null || s.isBlank()) return 0;
        return (int) Math.ceil(s.length() / 3.0);
        // 필요 시 더 정교한 추정기로 교체 가능
    }

    /** 원문 길이 트림(문자 기준) */
    private String trimForTokens(String content) {
        if (content == null) return "";
        int MAX_CHARS = 4000; // 요약에 충분한 길이
        return content.length() > MAX_CHARS
                ? content.substring(0, MAX_CHARS) + "\n\n...(truncated)"
                : content;
    }

    // =====================================================================
    //  F. Category 매칭 & 폴더 경로 보조 (요약 JSON 모드 결과를 사용할 때)
    // =====================================================================

    /** 로컬 DB 카테고리 매칭 → 최종 경로 결정(신뢰 낮으면 llmCategory fallback) */
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

    /** 대/중/소 경로대로 NoteFolder 생성/탐색 → 최하위 folderId 반환 */
    public Long ensureNoteFolderPath(String userId, CategoryPath path) {
        if (path == null) return null;
        Long parentId = null;
        parentId = findOrCreate(userId, parentId, path.getLarge());
        parentId = findOrCreate(userId, parentId, path.getMedium());
        parentId = findOrCreate(userId, parentId, path.getSmall());
        return parentId;
    }

    private Long findOrCreate(String userId, Long parentId, String name) {
        if (name == null || name.isBlank()) return parentId;

        List<NoteFolder> siblings = (parentId == null)
                ? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userId)
                : noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userId, parentId);

        Optional<NoteFolder> found = siblings.stream()
                .filter(f -> f.getFolderName().equals(name))
                .findFirst();
        if (found.isPresent()) return found.get().getFolderId();

        NoteFolder folder = NoteFolder.builder()
                .userIdx(userId)
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
    public TestSummary processText(String userId, String content, String promptTitle) {
        long start = System.currentTimeMillis();
        try {
            // RAW 마크다운으로 생성 (DB 프롬프트 그대로 적용)
            String markdown = runPromptMarkdown(userId, promptTitle, content);
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

    // (선택) 혹시 2인자 호출부가 있을 때 대비
    public TestSummary processText(String userId, String content) {
        return processText(userId, content, "심플버전");
    }

    // -----------------------------------------------------
// ✅ NotionController 호환용: 파일 3인자 버전 (userId, file, promptTitle)
// -----------------------------------------------------
    public TestSummary processFile(String userId, MultipartFile file, String promptTitle) {
        long start = System.currentTimeMillis();
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String markdown = runPromptMarkdown(userId, promptTitle, text);
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

    // (선택) 혹시 다른 순서로 호출되는 곳이 있다면, 순서 뒤바뀐 오버로드 추가
    public TestSummary processFile(String userId, String promptTitle, MultipartFile file) {
        return processFile(userId, file, promptTitle);
    }

}
