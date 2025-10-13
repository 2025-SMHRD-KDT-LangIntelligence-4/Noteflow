package com.smhrd.web.service;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.CategoryHierarchy;
import com.smhrd.web.repository.CategoryHierarchyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KeywordExtractionService {

    private final WebClient webClient;
    private final CategoryHierarchyRepository categoryHierarchyRepository;

    @Value("${vllm.api.model}")
    private String modelName;

    /**
     * vLLM을 통한 키워드 추출 및 자동 분류
     */
    public CategoryResult extractAndClassify(String title, String content) {
        try {
            // 1. vLLM API 호출하여 키워드 추출
            Set<String> extractedKeywords = extractKeywords(title, content);

            // 2. 추출된 키워드로 분류 체계 매칭
            CategoryHierarchy bestMatch = findBestCategory(extractedKeywords);

            // 3. 결과 반환
            return CategoryResult.builder()
                    .extractedKeywords(extractedKeywords)
                    .matchedCategory(bestMatch)
                    .confidence(calculateConfidence(extractedKeywords, bestMatch))
                    .suggestedFolderPath(generateFolderPath(bestMatch))
                    .build();

        } catch (Exception e) {
            // 실패 시 기본 분류
            return getDefaultCategory();
        }
    }

    /**
     * vLLM으로 키워드 추출
     */

    private Set<String> extractKeywords(String title, String content) {
        String prompt = buildKeywordExtractionPrompt(title, content);
        Map<String, Object> req = new HashMap<>();
       req.put("model", modelName);
        req.put("max_tokens", 200);
        req.put("temperature", 0.3);
        req.put("stream", false);
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", prompt);
        req.put("messages", List.of(user));
       try {
            String response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
           String extractedText = extractChatResponseText(response);
            return parseKeywords(extractedText);
        } catch (Exception e) {
            return extractBasicKeywords(title, content);
        }
    }


    /**
     * 키워드 추출 프롬프트 생성
     */
    private String buildKeywordExtractionPrompt(String title, String content) {
        return String.format("""
            다음 학습 내용에서 핵심 기술 키워드를 추출해주세요.
            프로그래밍 언어, 기술, 개념, 라이브러리, 프레임워크 등의 키워드를 찾아서 
            콤마(,)로 구분하여 나열해주세요.
            
            제목: %s
            내용: %s
            
            추출된 키워드:
            """, title, content);
    }

    /**
     * API 응답에서 텍스트 추출
     */

    private String extractChatResponseText(String responseJson) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseJson);
            return node.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }


    /**
     * 추출된 텍스트를 키워드 Set으로 변환
     */
    private Set<String> parseKeywords(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(extractedText.toLowerCase().split("[,\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.length() > 1)
                .collect(Collectors.toSet());
    }

    /**
     * 기본 키워드 추출 (vLLM 실패시 백업)
     */
    private Set<String> extractBasicKeywords(String title, String content) {
        Set<String> keywords = new HashSet<>();
        String combined = (title + " " + content).toLowerCase();

        // 프로그래밍 관련 기본 키워드들 체크
        String[] basicKeywords = {
                "java", "python", "javascript", "html", "css", "sql", "spring",
                "class", "function", "method", "variable", "array", "list", "map",
                "if", "for", "while", "try", "catch", "select", "insert", "update"
        };

        for (String keyword : basicKeywords) {
            if (combined.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return keywords;
    }

    /**
     * 추출된 키워드로 최적 분류 찾기
     */
    private CategoryHierarchy findBestCategory(Set<String> extractedKeywords) {
        List<CategoryHierarchy> allCategories = categoryHierarchyRepository.findAll();

        CategoryHierarchy bestMatch = null;
        int highestScore = 0;

        for (CategoryHierarchy category : allCategories) {
            int score = calculateMatchScore(extractedKeywords, category);
            if (score > highestScore) {
                highestScore = score;
                bestMatch = category;
            }
        }

        return bestMatch != null ? bestMatch : getDefaultCategoryEntity();
    }

    /**
     * 키워드 매칭 점수 계산
     */
    private int calculateMatchScore(Set<String> extractedKeywords, CategoryHierarchy category) {
        if (category.getKeywords() == null) return 0;

        Set<String> categoryKeywords = Arrays.stream(category.getKeywords().toLowerCase().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        int score = 0;
        for (String extracted : extractedKeywords) {
            for (String categoryKeyword : categoryKeywords) {
                if (extracted.contains(categoryKeyword) || categoryKeyword.contains(extracted)) {
                    score += 10; // 정확한 매치
                } else if (isSimilar(extracted, categoryKeyword)) {
                    score += 5; // 유사한 매치
                }
            }
        }

        return score;
    }

    /**
     * 단어 유사도 체크 (간단한 편집 거리)
     */
    private boolean isSimilar(String word1, String word2) {
        if (word1.length() < 3 || word2.length() < 3) return false;

        int distance = editDistance(word1, word2);
        int maxLength = Math.max(word1.length(), word2.length());

        return (double) distance / maxLength < 0.3; // 30% 이하 차이면 유사
    }

    /**
     * 편집 거리 계산 (Levenshtein Distance)
     */
    private int editDistance(String word1, String word2) {
        int[][] dp = new int[word1.length() + 1][word2.length() + 1];

        for (int i = 0; i <= word1.length(); i++) {
            for (int j = 0; j <= word2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }

        return dp[word1.length()][word2.length()];
    }

    /**
     * 매칭 신뢰도 계산
     */
    private double calculateConfidence(Set<String> extractedKeywords, CategoryHierarchy category) {
        if (category == null || extractedKeywords.isEmpty()) return 0.0;

        int matchScore = calculateMatchScore(extractedKeywords, category);
        int maxPossibleScore = extractedKeywords.size() * 10;

        return maxPossibleScore > 0 ? (double) matchScore / maxPossibleScore : 0.0;
    }

    /**
     * 폴더 경로 생성
     */
    private String generateFolderPath(CategoryHierarchy category) {
        if (category == null) return "기타/미분류";

        return String.format("%s/%s/%s",
                category.getLargeCategory(),
                category.getMediumCategory(),
                category.getSmallCategory());
    }

    /**
     * 기본 분류 반환
     */
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
                .exampleTag("[기타][미분류][일반]")
                .build();
    }
}
