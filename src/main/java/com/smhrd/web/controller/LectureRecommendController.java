package com.smhrd.web.controller;

import com.smhrd.web.entity.Lecture;
import com.smhrd.web.repository.LectureRepository;
import com.smhrd.web.repository.LectureSearchRepository;
import com.smhrd.web.repository.LectureTagRepository;
import com.smhrd.web.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/lecture")
public class LectureRecommendController {

    private final LectureRepository lectureRepository;
    private final LectureSearchRepository lectureSearchRepository;
    private final LectureTagRepository lectureTagRepository;

    /**
     * 강의 추천 페이지
     */
    @GetMapping("/recommend")
    public String recommendPage(
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String categoryPath,
            @RequestParam(required = false) Long noteId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        model.addAttribute("pageTitle", "강의 추천");
        model.addAttribute("activeMenu", "lectureRecommend");

        if (userDetails instanceof CustomUserDetails cud) {
            model.addAttribute("nickname", cud.getNickname());
            model.addAttribute("email", cud.getEmail());
        }

        model.addAttribute("keywords", keywords);
        model.addAttribute("categoryPath", categoryPath);
        model.addAttribute("noteId", noteId);

        return "recomLecture";
    }

    /**
     * 강의 추천 API (POST)
     */
    @PostMapping("/api/recommend")
    @ResponseBody
    public Map<String, Object> recommend(@RequestBody RecommendRequest req) {
        log.info("🔍 강의 추천 요청: tags={}, keyword={}, category={}, searchMode={}",
                req.tags, req.keyword, req.category, req.searchMode);
        
        int size = Math.max(1, Optional.ofNullable(req.size).orElse(30));
        List<Lecture> list = new ArrayList<>();
        
        try {
            // 태그 배열 검색
            if (req.tags != null && !req.tags.isEmpty()) {
                String mode = (req.searchMode != null) ? req.searchMode.toUpperCase() : "OR";
                log.info("📌 태그 배열 검색: {} (모드: {})", req.tags, mode);
                
                if ("AND".equals(mode)) {
                    list = searchByTagsAndEnhanced(req.tags, size); // ⭐ 개선된 AND 검색
                } else {
                    list = searchByTagsEnhanced(req.tags, size); // ⭐ 개선된 OR 검색
                }
            }
            // 카테고리 검색
            else if (req.category != null && req.category.getLarge() != null) {
                log.info("📁 카테고리 검색: {}", req.category);
                list = searchByCategory(req.category, size);
            }
            // 키워드 검색
            else if (req.keyword != null && !req.keyword.isBlank()) {
                log.info("🔤 키워드 검색: {}", req.keyword);
                list = searchByKeywords(req.keyword, size);
            }
            // 전체 조회
            else {
                log.info("📚 전체 강의 조회");
                list = lectureRepository
                        .findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .getContent();
            }
            
            log.info("✅ 검색 결과: {}개 강의", list.size());
            
            // 태그 정보 추가
            Map<Long, List<String>> tagMap = buildTagMap(list);
            
            // 응답 생성
            Map<String, Object> out = new HashMap<>();
            out.put("success", true);
            out.put("count", list.size());
            out.put("items", list.stream().map(l -> {
                Map<String, Object> item = new HashMap<>();
                item.put("lecIdx", l.getLecIdx());
                item.put("title", l.getLecTitle());
                item.put("url", l.getLecUrl());
                item.put("categoryLarge", l.getCategoryLarge());
                item.put("categoryMedium", l.getCategoryMedium());
                item.put("categorySmall", l.getCategorySmall());
                item.put("videoFileId", l.getVideoFileId());
                item.put("hasOfflineVideo", l.getVideoFileId() != null && !l.getVideoFileId().isBlank());
                item.put("tags", tagMap.getOrDefault(l.getLecIdx(), List.of()));
                return item;
            }).collect(Collectors.toList()));
            
            return out;
            
        } catch (Exception e) {
            log.error("❌ 강의 추천 에러", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("count", 0);
            errorResponse.put("items", List.of());
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 태그 배열로 검색
     */
    private List<Lecture> searchByTagsEnhanced(List<String> tags, int size) {
        List<String> cleanTags = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        
        log.info("  📌 정제된 태그: {}", cleanTags);
        
        Map<Long, ScoreInfo> scoreMap = new HashMap<>();
        
        for (String tag : cleanTags) {
            // 1) 태그 정확 매칭 (10점)
            List<Lecture> exactMatches = lectureSearchRepository
                    .findByTagNameExact(tag, PageRequest.of(0, size * 2));
            for (Lecture lec : exactMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(10, "정확태그:" + tag);
            }
            log.info("  ✅ '{}' 태그 정확 매칭: {}개", tag, exactMatches.size());
            
            // 2) 태그 포함 매칭 (3점)
            List<Lecture> containsMatches = lectureSearchRepository
                    .findByTagNameContains(tag, PageRequest.of(0, size * 2));
            for (Lecture lec : containsMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(3, "포함태그:" + tag);
            }
            log.info("  ✅ '{}' 태그 포함 매칭: {}개", tag, containsMatches.size());
            
            // 3) 제목 매칭 (5점)
            List<Lecture> titleMatches = lectureRepository
                    .findByLecTitleContainingOrderByCreatedAtDesc(tag);
            for (Lecture lec : titleMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(5, "제목:" + tag);
            }
            log.info("  ✅ '{}' 제목 매칭: {}개", tag, titleMatches.size());
            
            // 4) 카테고리 매칭 (2점)
            List<Lecture> categoryMatches = lectureRepository
                    .findByCategoryLargeContainingOrCategoryMediumContainingOrCategorySmallContaining(
                            tag, tag, tag);
            for (Lecture lec : categoryMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(2, "카테고리:" + tag);
            }
            log.info("  ✅ '{}' 카테고리 매칭: {}개", tag, categoryMatches.size());
        }
        
        // 스마트인재개발원 보너스
        for (ScoreInfo info : scoreMap.values()) {
            if (info.lecture.getLecTitle() != null &&
                    info.lecture.getLecTitle().contains("스마트인재개발원")) {
                boolean keywordInTitle = false;
                for (String tag : cleanTags) {
                    if (info.lecture.getLecTitle().toLowerCase()
                            .contains(tag.toLowerCase())) {
                        keywordInTitle = true;
                        break;
                    }
                }
                if (keywordInTitle) {
                    info.addScore(50, "🏆스마트인재개발원");
                }
            }
        }
        
        // 점수 순 정렬
        return scoreMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .map(si -> si.lecture)
                .limit(size)
                .collect(Collectors.toList());
    }
    
    /**
     * 개선된 AND 검색: 모든 검색어가 강의에 포함되어야 함 (태그+제목+카테고리)
     */
    private List<Lecture> searchByTagsAndEnhanced(List<String> tags, int size) {
        List<String> cleanTags = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        
        log.info("🔗 AND 검색 시작: {}", cleanTags);
        
        if (cleanTags.isEmpty()) {
            return List.of();
        }
        
        // 모든 강의 조회
        List<Lecture> allLectures = lectureRepository.findAll();
        
        // 태그 정보 미리 로드
        Map<Long, List<String>> tagMap = buildTagMap(allLectures);
        
        // 각 강의가 모든 검색어를 포함하는지 확인
        List<Lecture> results = allLectures.stream()
                .filter(lecture -> {
                    // 강의의 모든 텍스트 정보를 합침
                    String combinedText = (
                            lecture.getLecTitle() + " " +
                            String.join(" ", tagMap.getOrDefault(lecture.getLecIdx(), List.of())) + " " +
                            (lecture.getCategoryLarge() != null ? lecture.getCategoryLarge() : "") + " " +
                            (lecture.getCategoryMedium() != null ? lecture.getCategoryMedium() : "") + " " +
                            (lecture.getCategorySmall() != null ? lecture.getCategorySmall() : "")
                    ).toLowerCase();
                    
                    // 모든 검색어가 포함되어 있는지 확인
                    for (String tag : cleanTags) {
                        if (!combinedText.contains(tag.toLowerCase())) {
                            return false; // 하나라도 없으면 제외
                        }
                    }
                    return true; // 모두 포함
                })
                .limit(size)
                .collect(Collectors.toList());
        
        log.info("✅ AND 검색 완료: {}개 강의 (모든 검색어 포함)", results.size());
        
        return results;
    }

    /**
     * 카테고리로 검색
     */
    private List<Lecture> searchByCategory(CategoryDto category, int size) {
        String large = category.getLarge();
        String medium = category.getMedium();
        String small = category.getSmall();

        List<Lecture> results;

        if (small != null && !small.isBlank()) {
            results = lectureRepository
                    .findByCategoryLargeAndCategoryMediumAndCategorySmallOrderByCreatedAtDesc(
                            large, medium, small);
        } else if (medium != null && !medium.isBlank()) {
            results = lectureRepository
                    .findByCategoryLargeAndCategoryMediumOrderByCreatedAtDesc(large, medium);
        } else {
            results = lectureRepository
                    .findByCategoryLargeOrderByCreatedAtDesc(large);
        }

        log.info("  ✅ 카테고리 매칭: {}개", results.size());

        if (results.size() > size) {
            results = results.subList(0, size);
        }

        return results;
    }

    /**
     * 키워드로 스마트 검색 (점수 기반)
     */

    private List<Lecture> searchByKeywords(String keyword, int size) {
        String[] keywords = keyword.split(",");
        Map<Long, ScoreInfo> scoreMap = new HashMap<>();

        for (String kw : keywords) {
            String trimmed = kw.trim();
            if (trimmed.isBlank()) continue;

            // 1) 정확한 태그 매칭 (점수 10점)
            List<Lecture> exactMatches = lectureSearchRepository
                    .findByTagNameExact(trimmed, PageRequest.of(0, size));

            for (Lecture lec : exactMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(10, "정확:" + trimmed);
            }

            log.info("  ✅ '{}' 정확 매칭: {}개", trimmed, exactMatches.size());

            // 2) 포함 매칭 (점수 3점)
            List<Lecture> containsMatches = lectureSearchRepository
                    .findByTagNameContains(trimmed, PageRequest.of(0, size * 2));

            for (Lecture lec : containsMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(3, "포함:" + trimmed);
            }

            log.info("  ✅ '{}' 포함 매칭: {}개", trimmed, containsMatches.size());

            // 3) 제목 매칭 (점수 1점)
            List<Lecture> titleMatches = lectureRepository
                    .findByLecTitleContainingOrderByCreatedAtDesc(trimmed);

            for (Lecture lec : titleMatches) {
                scoreMap.computeIfAbsent(lec.getLecIdx(), k -> new ScoreInfo(lec))
                        .addScore(1, "제목:" + trimmed);
            }

            log.info("  ✅ '{}' 제목 매칭: {}개", trimmed, titleMatches.size());
        }

        // 4) 스마트인재개발원 보너스 (검색 키워드가 제목에 있을 때만!)
        for (ScoreInfo info : scoreMap.values()) {
            if (info.lecture.getLecTitle() != null &&
                    info.lecture.getLecTitle().contains("스마트인재개발원")) {

                // 검색한 키워드가 제목에 있는지 확인
                boolean keywordInTitle = false;
                for (String kw : keywords) {
                    String trimmed = kw.trim();
                    if (info.lecture.getLecTitle().toLowerCase()
                            .contains(trimmed.toLowerCase())) {
                        keywordInTitle = true;
                        break;
                    }
                }

                // 검색 키워드가 제목에 있으면 보너스!
                if (keywordInTitle) {
                    info.addScore(50, "🏆스마트인재개발원");
                    log.info("  🏆 스마트인재개발원 강의 발견: {}", info.lecture.getLecTitle());
                }
            }
        }

        // 점수 순으로 정렬
        List<Lecture> results = scoreMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .map(si -> si.lecture)
                .limit(size)
                .collect(Collectors.toList());

        // 상위 5개 로그 출력
        log.info("  📊 상위 5개 결과:");
        scoreMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .limit(5)
                .forEach(si -> log.info("    - {} ({}점) {}",
                        si.lecture.getLecTitle(), si.score, si.matchTypes));

        return results;
    }



    /**
     * 강의별 태그 목록 생성
     */
    private Map<Long, List<String>> buildTagMap(List<Lecture> lectures) {
        if (lectures.isEmpty()) {
            return Map.of();
        }

        List<Long> lecIds = lectures.stream()
                .map(Lecture::getLecIdx)
                .collect(Collectors.toList());

        List<Object[]> rows = lectureTagRepository.findTagsByLectureIds(lecIds);

        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] row : rows) {
            Long lecIdx = (Long) row[0];
            String tagName = (String) row[1];
            map.computeIfAbsent(lecIdx, k -> new ArrayList<>()).add(tagName);
        }

        return map;
    }

    /**
     * 점수 정보를 담는 내부 클래스
     */
    private static class ScoreInfo {
        Lecture lecture;
        int score = 0;
        List<String> matchTypes = new ArrayList<>();

        ScoreInfo(Lecture lecture) {
            this.lecture = lecture;
        }

        void addScore(int points, String type) {
            this.score += points;
            this.matchTypes.add(type);
        }
    }

    /**
     * 요청 DTO
     */
    public static class RecommendRequest {
        public List<String> tags;
        public CategoryDto category;
        public String keyword;
        public Integer size;
        public Boolean like;
        public String searchMode;  // 추가: "OR" 또는 "AND"

        @Override
        public String toString() {
            return "RecommendRequest{" +
                    "tags=" + tags +
                    ", category=" + category +
                    ", keyword='" + keyword + '\'' +
                    ", size=" + size +
                    ", like=" + like +
                    ", searchMode='" + searchMode + '\'' +  // ⭐ 추가
                    '}';
        }
    }

    /**
     * 카테고리 DTO
     */
    public static class CategoryDto {
        private String large;
        private String medium;
        private String small;

        public String getLarge() { return large; }
        public void setLarge(String large) { this.large = large; }

        public String getMedium() { return medium; }
        public void setMedium(String medium) { this.medium = medium; }

        public String getSmall() { return small; }
        public void setSmall(String small) { this.small = small; }

        @Override
        public String toString() {
            return "CategoryDto{" +
                    "large='" + large + '\'' +
                    ", medium='" + medium + '\'' +
                    ", small='" + small + '\'' +
                    '}';
        }
    }
}
