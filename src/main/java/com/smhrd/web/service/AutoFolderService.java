package com.smhrd.web.service;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.NoteFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFolderService {

    private final NoteFolderRepository noteFolderRepository;

    @Transactional
    public Long createOrFindFolder(Long userIdx, CategoryResult categoryResult) {
        if (categoryResult == null || !categoryResult.hasCategory()) {
            log.warn("CategoryResult가 비어있습니다.");
            return null;
        }

        List<String> levels = new ArrayList<>();
        String large = categoryResult.getLargeCategory();
        String medium = categoryResult.getMediumCategory();
        String small = categoryResult.getSmallCategory();

        if (large != null && !large.trim().isEmpty()) {
            levels.add(sanitize(large));
        }
        if (medium != null && !medium.trim().isEmpty()) {
            levels.add(sanitize(medium));
        }
        if (small != null && !small.trim().isEmpty()) {
            levels.add(sanitize(small));
        }

        if (levels.isEmpty()) {
            log.warn("폴더명이 비어있습니다.");
            return null;
        }

        log.info("폴더 생성/조회: {} (user={})", levels, userIdx);

        Long parentId = null;
        for (String folderName : levels) {
            parentId = getOrCreate(userIdx, parentId, folderName);
        }

        return parentId;
    }

    // ✅ 폴더 경로 생성 메서드 추가
    public String generateFolderPath(CategoryResult categoryResult) {
        if (categoryResult == null || !categoryResult.hasCategory()) {
            return "";
        }

        List<String> parts = new ArrayList<>();

        if (categoryResult.getLargeCategory() != null && !categoryResult.getLargeCategory().trim().isEmpty()) {
            parts.add(categoryResult.getLargeCategory());
        }
        if (categoryResult.getMediumCategory() != null && !categoryResult.getMediumCategory().trim().isEmpty()) {
            parts.add(categoryResult.getMediumCategory());
        }
        if (categoryResult.getSmallCategory() != null && !categoryResult.getSmallCategory().trim().isEmpty()) {
            parts.add(categoryResult.getSmallCategory());
        }

        // ✅ 구분자를 " > "로 설정 (슬래시 문제 해결)
        return String.join(" > ", parts);
    }

    private Long getOrCreate(Long userIdx, Long parentId, String folderName) {
        Optional<NoteFolder> existing = parentId == null
                ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName);

        if (existing.isPresent()) {
            log.debug("기존 폴더 사용: {} (id={})", folderName, existing.get().getFolderId());
            return existing.get().getFolderId();
        }

        try {
            NoteFolder folder = NoteFolder.builder()
                    .userIdx(userIdx)
                    .parentFolderId(parentId)
                    .folderName(folderName)
                    .sortOrder(0)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            NoteFolder saved = noteFolderRepository.save(folder);
            log.info("새 폴더 생성: {} (id={}, parent={})", folderName, saved.getFolderId(), parentId);
            return saved.getFolderId();
        } catch (DataIntegrityViolationException e) {
            log.warn("동시성 문제로 재조회: {}", folderName);
            return (parentId == null
                    ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                    : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName))
                    .map(NoteFolder::getFolderId)
                    .orElseThrow(() -> new RuntimeException("폴더 생성 실패: " + folderName, e));
        }
    }

    private String sanitize(String name) {
        if (name == null) return "";
        return name.replace("/", "／")
                .replaceAll("[\\\\:*?\"<>|]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
