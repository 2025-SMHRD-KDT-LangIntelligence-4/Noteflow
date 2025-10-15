package com.smhrd.web.service;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.NoteFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class AutoFolderService {

    private final NoteFolderRepository noteFolderRepository;

    @Transactional
    public Long createOrFindFolder(Long userIdx, CategoryResult categoryResult) {
        if (categoryResult == null || categoryResult.getSuggestedFolderPath() == null) return null;

        String[] levels = Arrays.stream(categoryResult.getSuggestedFolderPath().split("/"))
                .map(this::sanitize)
                .filter(s -> !s.isBlank())
                .limit(3)
                .toArray(String[]::new);

        Long parentId = null; // 루트부터
        for (String name : levels) {
            parentId = getOrCreate(userIdx, parentId, name);
        }
        return parentId; // 마지막 레벨 ID
    }

    private Long getOrCreate(Long userIdx, Long parentId, String folderName) {
        Optional<NoteFolder> existing = (parentId == null)
                ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName);

        if (existing.isPresent()) return existing.get().getFolderId();

        try {
            NoteFolder folder = NoteFolder.builder()
                    .userIdx(userIdx)
                    .parentFolderId(parentId)       // ✅ 핵심: 부모 연결 필수
                    .folderName(folderName)
                    .sortOrder(0)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return noteFolderRepository.save(folder).getFolderId();

        } catch (DataIntegrityViolationException e) {
            // 유니크 제약과의 경합: 재조회로 복구
            return (parentId == null
                    ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                    : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName))
                    .map(NoteFolder::getFolderId)
                    .orElseThrow(() -> e);
        }
    }


    private String sanitize(String name) {
        if (name == null) return "";
        return name.replace("/", "／")      // ✅ 소분류 내부 슬래시를 분리자로 오인하지 않게 치환
                .replaceAll("[\\t\\n\\r]", " ")
                .trim();
    }

}

