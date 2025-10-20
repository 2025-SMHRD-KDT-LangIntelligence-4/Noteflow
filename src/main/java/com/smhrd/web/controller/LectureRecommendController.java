package com.smhrd.web.controller;

import com.smhrd.web.entity.Lecture;
import com.smhrd.web.repository.LectureRepository;
import com.smhrd.web.repository.LectureSearchRepository;
import com.smhrd.web.repository.LectureTagRepository;
import com.smhrd.web.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    // 추천 페이지 진입
    // 예: /lecture/recommend?keywords=스프링,데이터&categoryPath=Spring%20%3E%20Data%20Access&noteId=123
    @GetMapping("/recommend")
    public String recommendPage(
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String categoryPath,
            @RequestParam(required = false) Long noteId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        model.addAttribute("pageTitle", "강의 추천");
        model.addAttribute("activeMenu", "lectureRecommend");
        if (userDetails instanceof CustomUserDetails cud) {
            model.addAttribute("nickname", cud.getNickname());
            model.addAttribute("email", cud.getEmail());
        }
        model.addAttribute("keywords", keywords);
        model.addAttribute("categoryPath", categoryPath);
        model.addAttribute("noteId", noteId);
        return "LectureRecommend"; // templates/LectureRecommend.html 과 정확히 일치
    }

    // 태그 기반 추천 (부분/정확)
    @GetMapping("/api/recommend/by-tags")
    @ResponseBody
    public Map<String, Object> recommendByTags(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean like) {

        var out = new HashMap<String, Object>();
        List<Lecture> list;

        if (!like) {
            List<String> names = Arrays.stream(q.split("[,\\s]+"))
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            list = lectureSearchRepository.findByTagNamesExact(names, PageRequest.of(0, Math.max(1, size)));
        } else {
            String kw = q.toLowerCase(Locale.ROOT).trim();
            list = lectureSearchRepository.findByTagNameLikePage(kw, PageRequest.of(0, Math.max(1, size)));
        }

        Map<Long, List<String>> tagMap = list.isEmpty() ? Map.of()
                : lectureTagRepository
                  .findTagNamesByLectureIds(list.stream().map(Lecture::getLecIdx).collect(Collectors.toList()))
                  .stream()
                  .collect(Collectors.groupingBy(
                      LectureTagRepository.LectureIdTagName::getLecIdx,
                      Collectors.mapping(LectureTagRepository.LectureIdTagName::getTagName, Collectors.toList())
                  ));

        out.put("success", true);
        out.put("items", list.stream().map(l -> Map.of(
                "lecIdx", l.getLecIdx(),
                "title", l.getLecTitle(),
                "url", l.getLecUrl(),
                "tags", tagMap.getOrDefault(l.getLecIdx(), List.of())
        )).collect(Collectors.toList()));
        return out;
    }

    // 카테고리 기반 추천
    @GetMapping("/api/recommend/by-category")
    @ResponseBody
    public Map<String, Object> recommendByCategory(
            @RequestParam String large,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String small,
            @RequestParam(defaultValue = "10") int size) {

        var out = new HashMap<String, Object>();
        List<Lecture> list;

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

        out.put("success", true);
        out.put("items", list.stream().map(l -> Map.of(
                "lecIdx", l.getLecIdx(),
                "title", l.getLecTitle(),
                "url", l.getLecUrl()
        )).collect(Collectors.toList()));
        return out;
    }

    // (옵션) 제목 키워드로 검색
    @GetMapping("/api/recommend/by-title")
    @ResponseBody
    public Map<String, Object> recommendByTitle(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int size) {

        var out = new HashMap<String, Object>();
        List<Lecture> list = lectureRepository.findByLecTitleContainingOrderByCreatedAtDesc(keyword);
        if (list.size() > size) list = list.subList(0, size);

        out.put("success", true);
        out.put("items", list.stream().map(l -> Map.of(
                "lecIdx", l.getLecIdx(),
                "title", l.getLecTitle(),
                "url", l.getLecUrl()
        )).collect(Collectors.toList()));
        return out;
    }
}