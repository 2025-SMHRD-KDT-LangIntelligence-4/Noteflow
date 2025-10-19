package com.smhrd.web.controller;

import com.smhrd.web.entity.Tag;
import com.smhrd.web.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TagController.java
@RestController
@RequestMapping("/api/tags")
public class TagController {

    @Autowired
    private TagRepository tagRepository;

    /**
     * 태그 검색 (자동완성용)
     * - 입력한 글자로 시작하는 태그 최대 10개 반환
     * - usage_count 높은 순으로 정렬
     */
    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<String>> searchTags(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> tags = tagRepository.findTop10ByNameStartingWithOrderByUsageCountDesc(query.trim());
        return ResponseEntity.ok(tags);
    }

    /**
     * 인기 태그 조회 (캐싱 권장)
     * - 사용 빈도 높은 태그 20개
     */
    @GetMapping("/popular")
    @ResponseBody
    @Cacheable("popularTags")  // 5분 캐싱
    public ResponseEntity<List<Map<String, Object>>> getPopularTags() {
        List<Tag> tags = tagRepository.findTop20ByOrderByUsageCountDesc();

        List<Map<String, Object>> result = tags.stream()
                .map(tag -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tag.getName());
                    map.put("count", tag.getUsageCount());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}

