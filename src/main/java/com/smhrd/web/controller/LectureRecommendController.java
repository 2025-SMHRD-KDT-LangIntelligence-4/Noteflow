package com.smhrd.web.controller;

import com.smhrd.web.entity.Lecture;
import com.smhrd.web.repository.LectureRepository;
import com.smhrd.web.repository.LectureSearchRepository;
import com.smhrd.web.repository.LectureTagRepository;
import com.smhrd.web.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/lecture")
public class LectureRecommendController {

    private final LectureRepository lectureRepository;
    private final LectureSearchRepository lectureSearchRepository;
    private final LectureTagRepository lectureTagRepository;

    // 1) 추천 페이지 (템플릿: recomLecture.html)
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
        // ★ 템플릿 파일명과 일치
        return "recomLecture";
    }

    // 2) 단일 추천 API (POST) - 주소 깔끔 유지
    @PostMapping("/api/recommend")
    @ResponseBody
    public Map<String, Object> recommend(@RequestBody RecommendRequest req) {
        int size = Math.max(1, Optional.ofNullable(req.size).orElse(10));
        List<Lecture> list;

        // 2-1) 태그 기반 (정확/부분)
        if (req.tags != null && !req.tags.isEmpty()) {
            if (Boolean.TRUE.equals(req.like)) {
                String kw = String.join(" ", req.tags).toLowerCase(Locale.ROOT).trim();
                list = lectureSearchRepository.findByTagNameLikePage(kw, PageRequest.of(0, size));
            } else {
                List<String> names = req.tags.stream()
                        .filter(Objects::nonNull)
                        .map(s -> s.toLowerCase(Locale.ROOT).trim())
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
                list = lectureSearchRepository.findByTagNamesExact(names, PageRequest.of(0, size));
            }
        }
        // 2-2) 카테고리 기반
        else if (req.category != null && req.category.getLarge() != null) {
            String large = req.category.getLarge();
            String medium = req.category.getMedium();
            String small = req.category.getSmall();
            if (small != null && !small.isBlank()) {
                list = lectureRepository
                        .findByCategoryLargeAndCategoryMediumAndCategorySmallOrderByCreatedAtDesc(large, medium, small);
            } else if (medium != null && !medium.isBlank()) {
                list = lectureRepository
                        .findByCategoryLargeAndCategoryMediumOrderByCreatedAtDesc(large, medium);
            } else {
                list = lectureRepository.findByCategoryLargeOrderByCreatedAtDesc(large);
            }
            if (list.size() > size) list = list.subList(0, size);
        }
        // 2-3) 제목 키워드
        else if (req.keyword != null && !req.keyword.isBlank()) {
            list = lectureRepository.findByLecTitleContainingOrderByCreatedAtDesc(req.keyword);
            if (list.size() > size) list = list.subList(0, size);
        }
        // 2-4) 기본(최신)
        else {
            list = lectureRepository
                    .findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .getContent();
        }

        Map<Long, List<String>> tagMap = buildTagMap(list);

        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("items", list.stream().map(l -> Map.of(
                "lecIdx", l.getLecIdx(),
                "title", l.getLecTitle(),
                "url", l.getLecUrl(),
                "categoryLarge", l.getCategoryLarge(),
                "categoryMedium", l.getCategoryMedium(),
                "categorySmall", l.getCategorySmall(),
                "tags", tagMap.getOrDefault(l.getLecIdx(), List.of())
        )).collect(Collectors.toList()));
        return out;
    }

    private Map<Long, List<String>> buildTagMap(List<Lecture> lectures) {
        if (lectures == null || lectures.isEmpty()) return Map.of();
        List<Long> ids = lectures.stream().map(Lecture::getLecIdx).collect(Collectors.toList());
        return lectureTagRepository.findTagNamesByLectureIds(ids).stream()
                .collect(Collectors.groupingBy(
                        LectureTagRepository.LectureIdTagName::getLecIdx,
                        Collectors.mapping(LectureTagRepository.LectureIdTagName::getTagName, Collectors.toList())
                ));
    }

    // 요청 DTO
    public static class RecommendRequest {
        public List<String> tags;   // ["자바","스프링"]
        public Boolean like;        // 부분 일치 여부
        public Category category;   // { large, medium, small }
        public String keyword;      // 제목 키워드
        public Integer size;        // 개수
    }
    public static class Category {
        private String large;
        private String medium;
        private String small;
        public String getLarge() { return large; }
        public String getMedium() { return medium; }
        public String getSmall() { return small; }
    }
}
