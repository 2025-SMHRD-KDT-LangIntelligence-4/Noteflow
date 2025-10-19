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

/**
 * 자동 폴더 생성 서비스
 * - CategoryResult 기반으로 폴더 계층 생성
 * - 대/중/소 분류를 폴더로 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFolderService {

    private final NoteFolderRepository noteFolderRepository;

    /**
     * CategoryResult를 기반으로 폴더 생성 또는 조회
     * @param userIdx 사용자 ID
     * @param categoryResult 카테고리 분류 결과
     * @return 최종 폴더 ID (대 > 중 > 소의 마지막 레벨)
     */
    @Transactional
    public Long createOrFindFolder(Long userIdx, CategoryResult categoryResult) {
        if (categoryResult == null || !categoryResult.hasCategory()) {
            log.warn("CategoryResult가 없거나 카테고리가 설정되지 않음");
            return null;
        }

        // ✅ CategoryResult에서 대/중/소 추출
        List<String> levels = new ArrayList<>();

        String large = categoryResult.getLargeCategory();
        String medium = categoryResult.getMediumCategory();
        String small = categoryResult.getSmallCategory();

        if (large != null && !large.trim().isEmpty()) {
            levels.add(sanitize(large));

            if (medium != null && !medium.trim().isEmpty()) {
                levels.add(sanitize(medium));

                if (small != null && !small.trim().isEmpty()) {
                    levels.add(sanitize(small));
                }
            }
        }

        if (levels.isEmpty()) {
            log.warn("폴더 레벨이 비어있음");
            return null;
        }

        log.info("폴더 생성/조회: {} (user={})", levels, userIdx);

        // ✅ 계층적으로 폴더 생성/조회
        Long parentId = null; // 루트부터 시작
        for (String folderName : levels) {
            parentId = getOrCreate(userIdx, parentId, folderName);
        }

        return parentId; // 마지막 레벨 폴더 ID
    }

    /**
     * 폴더 조회 또는 생성
     * @param userIdx 사용자 ID
     * @param parentId 부모 폴더 ID (null이면 루트)
     * @param folderName 폴더명
     * @return 폴더 ID
     */
    private Long getOrCreate(Long userIdx, Long parentId, String folderName) {
        // 1) 기존 폴더 조회
        Optional<NoteFolder> existing = (parentId == null)
                ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName);

        if (existing.isPresent()) {
            log.debug("기존 폴더 사용: {} (id={})", folderName, existing.get().getFolderId());
            return existing.get().getFolderId();
        }

        // 2) 새 폴더 생성
        try {
            NoteFolder folder = NoteFolder.builder()
                    .userIdx(userIdx)
                    .parentFolderId(parentId)       // ✅ 핵심: 부모 연결
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
            // ✅ 경쟁 상태 보정: unique 제약 충돌 시 재조회
            log.warn("폴더 생성 충돌 발생, 재조회: {}", folderName);

            return (parentId == null
                    ? noteFolderRepository.findRootByUserIdxAndFolderName(userIdx, folderName)
                    : noteFolderRepository.findByUserIdxAndParentFolderIdAndFolderName(userIdx, parentId, folderName))
                    .map(NoteFolder::getFolderId)
                    .orElseThrow(() -> new RuntimeException("폴더 생성/조회 실패: " + folderName, e));
        }
    }

    /**
     * 폴더명 정제 (특수문자 처리)
     * @param name 원본 폴더명
     * @return 정제된 폴더명
     */
    private String sanitize(String name) {
        if (name == null) return "";

        return name.replace("/", "／")      // ✅ 슬래시를 전각문자로 치환
                .replaceAll("[\\t\\n\\r]", " ")  // 제어문자 제거
                .replaceAll("\\s+", " ")         // 연속 공백 제거
                .trim();
    }
}
