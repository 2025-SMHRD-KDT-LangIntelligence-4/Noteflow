package com.smhrd.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ✅ ChatService에서 강의 추천 기능 호출용 Wrapper
 *
 * 기존 LectureRecommendService의 모든 기능을 래핑해서
 * ChatService에서 쉽게 호출할 수 있게 함
 *
 * 사용 사례:
 * 1. "Java 강의 추천해줘" → keyword 검색
 * 2. "객체지향 관련 강의" → 태그 검색
 * 3. "나 자바 약해" → 오답 기반 추천
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotLectureService {

    // ✅ 이미 있는 LectureRecommendService 사용 (검색 기능 포함!)
    private final LectureRecommendService lectureRecommendService;

    /**
     * 챗봇에서 호출할 통합 강의 검색
     *
     * 사용법:
     * - keyword 있음 + tag 없음: keyword 검색
     * - tag 있음 + keyword 없음: 태그 검색
     * - 둘 다 있음: 하이브리드 검색 (AND/OR 모드)
     */
    public Map<String, Object> searchLecturesForChat(
            String keyword,
            List<String> tags,
            String searchMode,  // "AND", "OR", "auto"
            String category) {

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("tags", tags);
        result.put("mode", searchMode);

        try {
            // 1️⃣ 키워드 검색만
            if ((keyword != null && !keyword.isEmpty()) && (tags == null || tags.isEmpty())) {
                log.info("🔍 챗봇 강의 검색 - 키워드 모드: {}", keyword);
                List<Map<String, Object>> lectures = lectureRecommendService.searchByKeyword(
                        keyword,
                        category,
                        10  // limit
                );
                result.put("lectures", lectures);
                result.put("count", lectures.size());
                result.put("search_type", "keyword");
                log.info("✅ 키워드 검색 완료: {} 개", lectures.size());
                return result;
            }

            // 2️⃣ 태그 검색만
            if ((tags != null && !tags.isEmpty()) && (keyword == null || keyword.isEmpty())) {
                log.info("🔍 챗봇 강의 검색 - 태그 모드: {}", tags);
                List<Map<String, Object>> lectures = lectureRecommendService.searchByTags(
                        tags,
                        searchMode.equals("AND") ? "AND" : "OR",
                        category,
                        10
                );
                result.put("lectures", lectures);
                result.put("count", lectures.size());
                result.put("search_type", "tag");
                log.info("✅ 태그 검색 완료: {} 개", lectures.size());
                return result;
            }

            // 3️⃣ 키워드 + 태그 (하이브리드)
            if ((keyword != null && !keyword.isEmpty()) && (tags != null && !tags.isEmpty())) {
                log.info("🔍 챗봇 강의 검색 - 하이브리드 모드: keyword={}, tags={}", keyword, tags);
                List<Map<String, Object>> lectures = lectureRecommendService.searchByKeywordAndTags(
                        keyword,
                        tags,
                        searchMode,
                        category,
                        10
                );
                result.put("lectures", lectures);
                result.put("count", lectures.size());
                result.put("search_type", "hybrid");
                log.info("✅ 하이브리드 검색 완료: {} 개", lectures.size());
                return result;
            }

            // 4️⃣ 검색어 없음
            log.warn("⚠️ 검색어 또는 태그가 없습니다");
            result.put("lectures", new ArrayList<>());
            result.put("count", 0);
            result.put("error", "검색어 또는 태그를 입력해주세요");
            return result;

        } catch (Exception e) {
            log.error("❌ 챗봇 강의 검색 중 오류", e);
            result.put("error", "강의 검색 중 오류가 발생했습니다: " + e.getMessage());
            result.put("count", 0);
            return result;
        }
    }

    /**
     * 챗봇에서 사용자 오답 기반 강의 추천
     */
    public Map<String, Object> recommendByWeakness(Long userId) {
        try {
            log.info("🎓 챗봇 오답 기반 강의 추천 - userId={}", userId);

            List<Map<String, Object>> lectures = lectureRecommendService.recommendLecturesByWeakness(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("recommendation_type", "weakness_based");
            result.put("lectures", lectures);
            result.put("count", lectures.size());
            result.put("message", lectures.isEmpty()
                    ? "아직 오답 데이터가 없어서 추천할 수 없습니다"
                    : "취약 주제 기반으로 추천하는 강의입니다");

            return result;

        } catch (Exception e) {
            log.error("❌ 오답 기반 추천 중 오류", e);
            return Map.of(
                    "error", "추천 중 오류가 발생했습니다",
                    "count", 0
            );
        }
    }

    /**
     * 챗봇에서 사용자 상세 약점 분석
     */
    public Map<String, Object> analyzeWeakness(Long userId) {
        try {
            log.info("📊 챗봇 상세 약점 분석 - userId={}", userId);
            return lectureRecommendService.getDetailedWeaknessAnalysis(userId);

        } catch (Exception e) {
            log.error("❌ 상세 분석 중 오류", e);
            return Map.of("error", "분석 중 오류가 발생했습니다");
        }
    }

    /**
     * 챗봇에서 인기 강의 추천
     */
    public Map<String, Object> getPopularLectures() {
        try {
            log.info("⭐ 챗봇 인기 강의 조회");

            List<Map<String, Object>> lectures = lectureRecommendService.getPopularLectures();

            return Map.of(
                    "recommendation_type", "popular",
                    "lectures", lectures,
                    "count", lectures.size(),
                    "message", "많이 수강하는 인기 강의입니다"
            );

        } catch (Exception e) {
            log.error("❌ 인기 강의 조회 실패", e);
            return Map.of("error", "조회 중 오류가 발생했습니다");
        }
    }

    /**
     * 자연어 질문 → 검색 파라미터 자동 변환
     *
     * 예시:
     * "자바 강의 추천해줘" → keyword=자바, searchMode=auto
     * "객체지향이랑 디자인패턴 강의 보여줘" → tags=[객체지향, 디자인패턴], mode=OR
     * "웹개발 근데 쉬운거만" → keyword=웹개발, category=쉬움
     */
    public Map<String, Object> parseChatbotQuery(String question) {
        Map<String, Object> parsed = new HashMap<>();

        try {
            log.info("🔍 챗봇 질문 파싱: {}", question);

            // 간단한 키워드 추출 (실제로는 NLP 모델 사용 권장)
            String lowerQ = question.toLowerCase();

            // 태그 키워드 (태그처럼 동작할 단어들)
            List<String> tags = new ArrayList<>();
            List<String> commonTags = Arrays.asList(
                    "자바", "python", "javascript", "c언어", "c++",
                    "객체지향", "함수형", "디자인패턴", "알고리즘",
                    "웹개발", "모바일", "데이터베이스", "클라우드",
                    "머신러닝", "인공지능", "보안", "네트워크"
            );

            for (String tag : commonTags) {
                if (lowerQ.contains(tag.toLowerCase())) {
                    tags.add(tag);
                }
            }

            // 난이도 추출
            String difficulty = null;
            if (lowerQ.contains("쉬운") || lowerQ.contains("기초") || lowerQ.contains("초급")) {
                difficulty = "easy";
            } else if (lowerQ.contains("어려운") || lowerQ.contains("고급") || lowerQ.contains("심화")) {
                difficulty = "hard";
            }

            // 검색 모드 결정
            String searchMode = "OR";  // 기본값: OR (여러 조건 중 하나라도 만족)
            if (lowerQ.contains("그리고") || lowerQ.contains("와") || lowerQ.contains("및")) {
                searchMode = "AND";  // AND: 모든 조건 만족
            }

            // 키워드 추출 (태그 제외한 나머지)
            String keyword = question;
            for (String tag : tags) {
                keyword = keyword.replaceAll("(?i)" + tag, "").trim();
            }
            keyword = keyword.replaceAll("강의|추천|보여|듣고싶|싶어|해줘|해?", "").trim();

            parsed.put("question", question);
            parsed.put("tags", tags);
            parsed.put("keyword", keyword.isEmpty() ? null : keyword);
            parsed.put("difficulty", difficulty);
            parsed.put("searchMode", searchMode);

            log.info("✅ 파싱 완료: tags={}, keyword={}, mode={}", tags, keyword, searchMode);
            return parsed;

        } catch (Exception e) {
            log.error("❌ 질문 파싱 중 오류", e);
            parsed.put("error", "질문 분석에 실패했습니다");
            return parsed;
        }
    }
}