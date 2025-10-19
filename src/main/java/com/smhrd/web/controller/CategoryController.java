package com.smhrd.web.controller;

import com.smhrd.web.entity.CategoryHierarchy;
import com.smhrd.web.repository.CategoryHierarchyRepository;
import com.smhrd.web.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 카테고리 계층 관리 API
 * - 공개 카테고리 (user_idx = NULL): 모든 사용자가 볼 수 있음
 * - 개인 카테고리 (user_idx = 특정값): 해당 사용자만 볼 수 있음
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryHierarchyRepository categoryRepository;

    /**
     * 카테고리 계층 조회 (공개 + 본인 것만)
     * GET /api/categories/hierarchy
     */
    @GetMapping("/hierarchy")
    public ResponseEntity<Map<String, Object>> getCategoryHierarchy(Authentication auth) {
        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();

        // ✅ 공개 카테고리 + 본인 카테고리만 조회
        List<CategoryHierarchy> all = categoryRepository.findAllForUser(userIdx);

        // 대분류 추출
        Set<String> largeCategories = all.stream()
                .map(CategoryHierarchy::getLargeCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 계층 구조 생성
        Map<String, Map<String, Set<String>>> hierarchy = new HashMap<>();

        for (CategoryHierarchy cat : all) {
            String large = cat.getLargeCategory();
            String medium = cat.getMediumCategory();
            String small = cat.getSmallCategory();

            hierarchy.putIfAbsent(large, new HashMap<>());

            if (medium != null) {
                hierarchy.get(large).putIfAbsent(medium, new LinkedHashSet<>());

                if (small != null) {
                    hierarchy.get(large).get(medium).add(small);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("largeCategories", largeCategories);
        result.put("hierarchy", hierarchy);

        return ResponseEntity.ok(result);
    }

    /**
     * 카테고리 추가 (본인 소유)
     * POST /api/categories/add
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addCategory(
            @RequestBody Map<String, String> req,
            Authentication auth) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();

        String large = req.get("large");
        String medium = req.get("medium");
        String small = req.get("small");

        Map<String, Object> result = new HashMap<>();

        if (large == null || large.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "대분류는 필수입니다.");
            return ResponseEntity.badRequest().body(result);
        }

        // ✅ 본인 카테고리 중복 체크
        Optional<CategoryHierarchy> existing = categoryRepository
                .findByLargeCategoryAndMediumCategoryAndSmallCategoryAndUserIdx(
                        large.trim(),
                        medium != null ? medium.trim() : null,
                        small != null ? small.trim() : null,
                        userIdx
                );

        if (existing.isPresent()) {
            result.put("success", false);
            result.put("message", "이미 존재하는 카테고리입니다.");
            return ResponseEntity.ok(result);
        }

        // ✅ 개인 카테고리로 저장
        CategoryHierarchy category = new CategoryHierarchy();
        category.setLargeCategory(large.trim());
        category.setMediumCategory(medium != null ? medium.trim() : null);
        category.setSmallCategory(small != null ? small.trim() : null);
        category.setUserIdx(userIdx);  // ✅ 소유자 설정
        category.setIsPublic(false);   // ✅ 기본 비공개

        categoryRepository.save(category);

        result.put("success", true);
        result.put("message", "카테고리가 추가되었습니다. (개인 전용)");
        result.put("categoryId", category.getCategoryId());

        return ResponseEntity.ok(result);
    }

    /**
     * 특정 대분류의 중분류 목록 조회
     * GET /api/categories/medium?large=국어
     */
    @GetMapping("/medium")
    public ResponseEntity<List<String>> getMediumCategories(
            @RequestParam String large,
            Authentication auth) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        List<String> mediums = categoryRepository.findDistinctMediumCategoriesForUser(large, userIdx);

        return ResponseEntity.ok(mediums);
    }

    /**
     * 특정 대분류+중분류의 소분류 목록 조회
     * GET /api/categories/small?large=국어&medium=문법
     */
    @GetMapping("/small")
    public ResponseEntity<List<String>> getSmallCategories(
            @RequestParam String large,
            @RequestParam String medium,
            Authentication auth) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        List<String> smalls = categoryRepository.findDistinctSmallCategoriesForUser(large, medium, userIdx);

        return ResponseEntity.ok(smalls);
    }

    /**
     * 카테고리 삭제 (본인 것만)
     * DELETE /api/categories/{categoryId}
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Map<String, Object>> deleteCategory(
            @PathVariable Long categoryId,
            Authentication auth) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        Map<String, Object> result = new HashMap<>();

        Optional<CategoryHierarchy> category = categoryRepository.findById(categoryId);

        if (category.isEmpty()) {
            result.put("success", false);
            result.put("message", "카테고리를 찾을 수 없습니다.");
            return ResponseEntity.notFound().build();
        }

        // ✅ 권한 체크: 본인 것만 삭제 가능
        if (category.get().getUserIdx() == null || !category.get().getUserIdx().equals(userIdx)) {
            result.put("success", false);
            result.put("message", "삭제 권한이 없습니다. (본인이 만든 카테고리만 삭제 가능)");
            return ResponseEntity.status(403).body(result);
        }

        categoryRepository.deleteById(categoryId);

        result.put("success", true);
        result.put("message", "카테고리가 삭제되었습니다.");

        return ResponseEntity.ok(result);
    }

    /**
     * 카테고리 공개 설정 변경
     * PATCH /api/categories/{categoryId}/visibility
     */
    @PatchMapping("/{categoryId}/visibility")
    public ResponseEntity<Map<String, Object>> updateVisibility(
            @PathVariable Long categoryId,
            @RequestBody Map<String, Boolean> req,
            Authentication auth) {

        Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
        Map<String, Object> result = new HashMap<>();

        Optional<CategoryHierarchy> category = categoryRepository.findById(categoryId);

        if (category.isEmpty()) {
            result.put("success", false);
            result.put("message", "카테고리를 찾을 수 없습니다.");
            return ResponseEntity.notFound().build();
        }

        // ✅ 권한 체크
        if (category.get().getUserIdx() == null || !category.get().getUserIdx().equals(userIdx)) {
            result.put("success", false);
            result.put("message", "권한이 없습니다.");
            return ResponseEntity.status(403).body(result);
        }

        Boolean isPublic = req.get("isPublic");
        category.get().setIsPublic(isPublic);
        categoryRepository.save(category.get());

        result.put("success", true);
        result.put("message", isPublic ? "공개로 설정되었습니다." : "비공개로 설정되었습니다.");

        return ResponseEntity.ok(result);
    }
}
