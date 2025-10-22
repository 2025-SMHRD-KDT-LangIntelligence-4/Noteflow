package com.smhrd.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.CategoryHierarchy;
import com.smhrd.web.repository.CategoryHierarchyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KeywordExtractionService {

    private final WebClient webClient;
    private final CategoryHierarchyRepository categoryHierarchyRepository;
    public KeywordExtractionService(@Qualifier("vllmApiClient") WebClient webClient, CategoryHierarchyRepository categoryHierarchyRepository /* 기타 파라미터 */) {
        this.webClient = webClient;
        // ... 초기화
        this.categoryHierarchyRepository = categoryHierarchyRepository;
    }
    @Value("${vllm.api.model}")
    private String modelName;

    /**
     * 이미 요약된 텍스트에서 키워드 추출 + 카테고리 매칭 (LLM + RAG)
     */
    public CategoryResult extractAndClassifyWithRAG(String title, String content, Long userIdx) {
        // ✅ 제목 + 내용 결합
        String combined = (title + "\n" + content).trim();

        // 1) 키워드 추출
        Set<String> keywords = extractKeywords(combined);

        // 2) 카테고리 매칭 (공개 + 본인 것만)
        List<CategoryHierarchy> candidates = categoryHierarchyRepository
                .findCandidatesByKeywordsForUser(keywords, userIdx);

        if (candidates.isEmpty()) {
            return CategoryResult.builder()
                    .extractedKeywords(keywords)
                    .confidence(0.0)
                    .build();
        }

        // 3) 가장 적합한 카테고리 선택
        CategoryHierarchy best = candidates.get(0);

        return CategoryResult.builder()
                .extractedKeywords(keywords)
                .matchedCategory(best)
                .largeCategory(best.getLargeCategory())
                .mediumCategory(best.getMediumCategory())
                .smallCategory(best.getSmallCategory())
                .confidence(0.8)
                .build();
    }


    /**
     * LLM 호출: 키워드 추출
     */
    private Set<String> extractKeywords(String text) {
        String prompt = "다음 텍스트에서 핵심 기술 키워드를 한국어로만 추출하세요. 불필요한 설명이나 라벨(예: '한국어 키워드:')은 쓰지 말고, 키워드만 콤마로 구분하여 출력하세요:\n" + text;
        String response = callLLM(prompt, 300);
        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * fallback: 기본 키워드 추출
     */
    private Set<String> extractBasicKeywords(String title, String content) {
        Set<String> keywords = new HashSet<>();
        String combined = (title + " " + content).toLowerCase();
        String[] basicKeywords = {
                "java", "python", "javascript", "html", "css", "sql", "spring",
                "class", "function", "method", "variable", "array", "list", "map"
        };
        for (String keyword : basicKeywords) {
            if (combined.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    /**
     * LLM 호출: 후보 카테고리 랭킹 + confidence 점수
     */
    private CategoryHierarchy rankCategories(String summary, List<CategoryHierarchy> candidates) {
        if (candidates.isEmpty()) return getDefaultCategoryEntity();

        String candidateList = candidates.stream()
                .map(c -> String.format("[%s/%s/%s]", c.getLargeCategory(), c.getMediumCategory(), c.getSmallCategory()))
                .collect(Collectors.joining(", "));
        String prompt = String.format(
                "요약: %s\n후보 카테고리: %s\n카테고리 이름은 한국어로만 작성하세요. 불필요한 설명 없이 카테고리명만 출력하세요. 응답 형식: 카테고리명|점수",
                summary, candidateList
        );

        String response = callLLM(prompt, 200);
        String[] parts = response.split("\\|");
        String bestCategoryName = parts[0].trim();
        double score = parts.length > 1 ? parseScore(parts[1].trim()) : 0.0;

        CategoryHierarchy bestMatch = candidates.stream()
                .filter(c -> bestCategoryName.contains(c.getSmallCategory()))
                .findFirst()
                .orElse(candidates.get(0));

        bestMatch.setConfidenceScore(score);
        return bestMatch;
    }

    private double parseScore(String scoreText) {
        try {
            return Double.parseDouble(scoreText);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * vLLM API 호출
     */
    private String callLLM(String prompt, int maxTokens) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", modelName);
        req.put("max_tokens", maxTokens);
        req.put("temperature", 0.3);
        req.put("stream", false);
        req.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        String response = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractText(response);
    }

    private String extractText(String responseJson) {
        try {
            var node = new ObjectMapper().readTree(responseJson);
            return node.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String cleanSegment(String s) {
        return s == null ? "" : s.replace("/", "／").trim();
    }



    private String generateFolderPath(CategoryHierarchy category) {
        if (category == null) return "기타/미분류/일반";
        return String.format("%s/%s/%s",
                cleanSegment(category.getLargeCategory()),
                cleanSegment(category.getMediumCategory()),
                cleanSegment(category.getSmallCategory()));
    }


    private CategoryResult getDefaultCategory() {
        return CategoryResult.builder()
                .extractedKeywords(new HashSet<>())
                .matchedCategory(getDefaultCategoryEntity())
                .confidence(0.0)
                .suggestedFolderPath("기타/미분류")
                .build();
    }

    private CategoryHierarchy getDefaultCategoryEntity() {
        return CategoryHierarchy.builder()
                .largeCategory("기타")
                .mediumCategory("미분류")
                .smallCategory("일반")
                .build();
    }
}
