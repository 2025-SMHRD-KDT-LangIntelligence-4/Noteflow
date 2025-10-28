package com.smhrd.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 챗봇에서 사용할 강의 추천 서비스
 * LectureRecommendService를 래핑하여 ChatService에서 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotLectureService {
    
    private final LectureRecommendService lectureRecommendService;
    
    /**
     * 챗봇용 강의 검색 (메인 메서드)
     * 키워드를 자동으로 파싱하여 태그로 변환하고 검색
     */
    public Map<String, Object> searchLecturesForChat(String keyword, List<String> tags, String searchMode, String category) {
        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("tags", tags);
        result.put("mode", searchMode != null ? searchMode : "OR");
        
        try {
            log.info("🔍 챗봇 강의 검색 시작 - 키워드: {}, 태그: {}", keyword, tags);
            
            // 키워드에서 자동으로 태그 추출
            List<String> autoTags = extractTagsFromKeyword(keyword);
            
            // 기존 태그와 자동 추출된 태그 병합
            Set<String> allTags = new HashSet<>();
            if (tags != null) allTags.addAll(tags);
            if (autoTags != null) allTags.addAll(autoTags);
            
            List<String> finalTags = new ArrayList<>(allTags);
            log.info("📌 최종 검색 태그: {}", finalTags);
            
            List<Map<String, Object>> lectures = new ArrayList<>();
            
            // 1. 태그가 있으면 태그 우선 검색 (웹페이지와 동일한 로직)
            if (!finalTags.isEmpty()) {
                log.info("🏷️ 태그 기반 검색: {}", finalTags);
                lectures = lectureRecommendService.searchByTags(finalTags, "OR", category, 20);
                result.put("searchType", "tag_based");
                log.info("✅ 태그 검색 완료: {}개", lectures.size());
            }
            
            // 2. 태그 검색 결과가 부족하면 키워드 검색 추가
            if (lectures.size() < 10 && keyword != null && !keyword.trim().isEmpty()) {
                log.info("🔍 키워드 검색 추가: {}", keyword);
                List<Map<String, Object>> keywordLectures = lectureRecommendService.searchByKeyword(keyword, category, 15);
                
                // 중복 제거하며 병합
                Set<String> existingIds = lectures.stream()
                    .map(l -> String.valueOf(l.get("id")))
                    .collect(Collectors.toSet());
                
                for (Map<String, Object> lecture : keywordLectures) {
                    String lectureId = String.valueOf(lecture.get("id"));
                    if (!existingIds.contains(lectureId)) {
                        lectures.add(lecture);
                        existingIds.add(lectureId);
                    }
                }
                
                result.put("searchType", finalTags.isEmpty() ? "keyword_only" : "hybrid");
                log.info("✅ 키워드 검색 추가 완료: 총 {}개", lectures.size());
            }
            
            // 3. 여전히 결과가 없으면 인기 강의 추천
            if (lectures.isEmpty()) {
                log.info("📈 검색 결과가 없어 인기 강의 추천");
                lectures = lectureRecommendService.getPopularLectures();
                lectures = lectures.stream().limit(10).collect(Collectors.toList());
                result.put("searchType", "popular_fallback");
            }
            
            // 결과 제한 (최대 15개)
            if (lectures.size() > 15) {
                lectures = lectures.stream().limit(15).collect(Collectors.toList());
            }
            
            result.put("lectures", lectures);
            result.put("count", lectures.size());
            result.put("success", true);
            
            log.info("🎯 챗봇 강의 검색 완료: {}개 결과", lectures.size());
            return result;
            
        } catch (Exception e) {
            log.error("❌ 챗봇 강의 검색 실패: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            result.put("count", 0);
            result.put("lectures", new ArrayList<>());
            result.put("success", false);
            return result;
        }
    }
    
    /**
     * 키워드에서 자동으로 태그 추출
     */
    private List<String> extractTagsFromKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> extractedTags = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        
        // 주요 기술 키워드들
        List<String> techKeywords = Arrays.asList(
            "자바", "java", "파이썬", "python", "자바스크립트", "javascript",
            "리액트", "react", "스프링", "spring", "노드", "node.js", "nodejs",
            "도커", "docker", "쿠버네티스", "kubernetes", "aws", "클라우드",
            "리눅스", "linux", "우분투", "ubuntu", "데이터베이스", "mysql", "postgresql",
            "머신러닝", "딥러닝", "ai", "인공지능", "빅데이터", "데이터분석",
            "웹개발", "프론트엔드", "백엔드", "풀스택", "안드로이드", "ios",
            "게임개발", "유니티", "unity", "언리얼", "블록체인", "보안", "네트워크"
        );
        
        for (String tech : techKeywords) {
            if (lowerKeyword.contains(tech.toLowerCase())) {
                extractedTags.add(tech);
            }
        }
        
        log.info("🏷️ 키워드 '{}' 에서 추출된 태그: {}", keyword, extractedTags);
        return extractedTags;
    }
    
    /**
     * 취약점 기반 강의 추천
     */
    public Map<String, Object> recommendByWeakness(Long userId) {
        try {
            log.info("📊 취약점 기반 강의 추천 - 사용자: {}", userId);
            List<Map<String, Object>> lectures = lectureRecommendService.recommendLecturesByWeakness(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("recommendationType", "weakness-based");
            result.put("lectures", lectures);
            result.put("count", lectures.size());
            result.put("message", lectures.isEmpty() ? 
                "아직 충분한 학습 데이터가 없어 개인화 추천이 어렵습니다. 인기 강의를 확인해보세요!" : 
                "회원님의 학습 취약점을 분석하여 맞춤 강의를 추천해드렸습니다.");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 취약점 분석 실패: {}", e.getMessage(), e);
            return Map.of("error", "취약점 분석 중 오류가 발생했습니다.", "count", 0);
        }
    }
    
    /**
     * 취약점 분석
     */
    public Map<String, Object> analyzeWeakness(Long userId) {
        try {
            log.info("📊 사용자 취약점 분석 - 사용자: {}", userId);
            return lectureRecommendService.getDetailedWeaknessAnalysis(userId);
        } catch (Exception e) {
            log.error("❌ 취약점 분석 실패: {}", e.getMessage(), e);
            return Map.of("error", "취약점 분석 중 오류가 발생했습니다.");
        }
    }
    
    /**
     * 인기 강의 조회
     */
    public Map<String, Object> getPopularLectures() {
        try {
            log.info("📈 인기 강의 조회");
            List<Map<String, Object>> lectures = lectureRecommendService.getPopularLectures();
            
            return Map.of(
                "recommendationType", "popular",
                "lectures", lectures,
                "count", lectures.size(),
                "message", "현재 가장 인기있는 강의들입니다!"
            );
        } catch (Exception e) {
            log.error("❌ 인기 강의 조회 실패: {}", e.getMessage(), e);
            return Map.of("error", "인기 강의 조회 중 오류가 발생했습니다.");
        }
    }
    
    /**
     * 챗봇 질의 파싱 (기존 유지)
     */
    public Map<String, Object> parseChatbotQuery(String question) {
        Map<String, Object> parsed = new HashMap<>();
        
        try {
            log.info("🔍 챗봇 질문 파싱: {}", question);
            
            String lowerQ = question.toLowerCase();
            List<String> tags = new ArrayList<>();
            
            // 기본 태그들
            List<String> commonTags = Arrays.asList(
                "자바", "python", "javascript", "c++", "c#", "php", "swift", "kotlin",
                "react", "vue", "angular", "spring", "django", "express",
                "mysql", "postgresql", "mongodb", "redis", "elasticsearch",
                "aws", "docker", "kubernetes", "linux", "git"
            );
            
            for (String tag : commonTags) {
                if (lowerQ.contains(tag.toLowerCase())) {
                    tags.add(tag);
                }
            }
            
            // 난이도 추출
            String difficulty = null;
            if (lowerQ.contains("초급") || lowerQ.contains("기초") || lowerQ.contains("입문")) {
                difficulty = "easy";
            } else if (lowerQ.contains("고급") || lowerQ.contains("심화") || lowerQ.contains("전문")) {
                difficulty = "hard";
            }
            
            // 검색 모드
            String searchMode = "OR";
            if (lowerQ.contains("모두") || lowerQ.contains("전부") || lowerQ.contains("동시에")) {
                searchMode = "AND";
            }
            
            // 키워드 정제
            String keyword = question;
            for (String tag : tags) {
                keyword = keyword.replaceAll("(?i)" + tag, "").trim();
            }
            keyword = keyword.replaceAll("\\s+", " ").trim();
            
            parsed.put("question", question);
            parsed.put("tags", tags);
            parsed.put("keyword", keyword.isEmpty() ? null : keyword);
            parsed.put("difficulty", difficulty);
            parsed.put("searchMode", searchMode);
            
            log.info("✅ 파싱 완료: tags={}, keyword={}, mode={}", tags, keyword, searchMode);
            return parsed;
            
        } catch (Exception e) {
            log.error("❌ 질문 파싱 실패: {}", e.getMessage(), e);
            parsed.put("error", "질문 파싱 중 오류가 발생했습니다.");
            return parsed;
        }
    }
}