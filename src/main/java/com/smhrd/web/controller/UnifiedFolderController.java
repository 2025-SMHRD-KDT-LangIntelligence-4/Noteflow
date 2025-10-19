package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.FileMetadataService;
import com.smhrd.web.service.FolderService;
import com.smhrd.web.service.UnifiedFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/unified")
@RequiredArgsConstructor
public class UnifiedFolderController {

    private final UnifiedFolderService unifiedFolderService;
    private final NoteFolderRepository noteFolderRepository;
    private final FolderService folderService;
    private final FileMetadataService fileMetadataService;

    // ========== 유틸 ==========
    private boolean isAnonymous(Authentication auth) {
        return auth == null || !auth.isAuthenticated() ||
                "anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "인증이 필요합니다."));
    }

    private Long getUserIdx(Authentication auth) {
        return ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
    }

    // ========== 트리 조회 ==========

    @GetMapping("/notes/tree")
    public ResponseEntity<Map<String, Object>> getNoteTree(Authentication auth) {
        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        Map<String, Object> result = new HashMap<>();
        result.put("folders", unifiedFolderService.getNoteFolderTree(userIdx));
        result.put("rootNotes", unifiedFolderService.getRootNotes(userIdx));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/files/tree")
    public ResponseEntity<Map<String, Object>> getFileTree(Authentication auth) {
        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        Map<String, Object> result = new HashMap<>();
        result.put("folders", folderService.getFileFolderTree(userIdx));
        result.put("rootFiles", fileMetadataService.getRootFiles(userIdx));
        return ResponseEntity.ok(result);
    }

    // ========== NoteFolder 관리 ==========

    @PostMapping("/notes/folder")
    public ResponseEntity<Map<String, Object>> createNoteFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) Long parentFolderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            Long folderId = unifiedFolderService.createNoteFolder(userIdx, folderName, parentFolderId);
            return ResponseEntity.ok(Map.of("success", true, "folderId", folderId));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/note-folders/{folderId}/move")
    public ResponseEntity<Map<String, Object>> moveNoteFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, Long> payload,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            Long targetFolderId = payload.get("targetFolderId");

            // 순환 참조 체크
            if (targetFolderId != null && isDescendant(folderId, targetFolderId)) {
                return ResponseEntity.ok(Map.of("success", false, "message", "하위 폴더로 이동할 수 없습니다."));
            }

            unifiedFolderService.moveNoteFolderWithMerge(userIdx, folderId, targetFolderId);
            return ResponseEntity.ok(Map.of("success", true, "message", "폴더가 이동되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/notes/folder/{folderId}/rename")
    public ResponseEntity<Map<String, Object>> renameNoteFolder(
            @PathVariable Long folderId,
            @RequestParam String newName,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            unifiedFolderService.renameNoteFolder(userIdx, folderId, newName);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/notes/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteNoteFolder(
            @PathVariable Long folderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            unifiedFolderService.deleteNoteFolder(userIdx, folderId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ========== Note 이동 ==========

    @PutMapping("/notes/{noteId}/move")
    public ResponseEntity<Map<String, Object>> moveNote(
            @PathVariable Long noteId,
            @RequestBody Map<String, Long> payload,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            Long targetFolderId = payload.get("targetFolderId");
            unifiedFolderService.moveNoteToFolder(userIdx, noteId, targetFolderId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ========== Folder (MongoDB) 관리 ==========

    @PutMapping("/folders/{folderId}/move")
    public ResponseEntity<Map<String, Object>> moveFileFolder(
            @PathVariable String folderId,
            @RequestBody Map<String, String> payload,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            String targetParentId = payload.get("targetFolderId");
            folderService.moveFolder(userIdx, folderId, targetParentId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ========== File 이동 ==========

    @PutMapping("/files/{fileId}/move")
    public ResponseEntity<Map<String, Object>> moveFile(
            @PathVariable String fileId,
            @RequestBody Map<String, String> payload,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            String targetFolderId = payload.get("targetFolderId");
            fileMetadataService.moveFile(userIdx, fileId, targetFolderId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/files/folder")
    public ResponseEntity<Map<String, Object>> createFileFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) String parentFolderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();

        Long userIdx = getUserIdx(auth);
        Map<String, Object> result = new HashMap<>();

        try {
            String folderId = folderService.createFolder(userIdx, folderName, parentFolderId);
            result.put("success", true);
            result.put("folderId", folderId);
            result.put("message", "폴더가 생성되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @DeleteMapping("/files/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteFileFolder(
            @PathVariable String folderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();

        Long userIdx = getUserIdx(auth);
        Map<String, Object> result = new HashMap<>();

        try {
            folderService.deleteFolder(userIdx, folderId);
            result.put("success", true);
            result.put("message", "폴더가 삭제되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    // ========== 유틸: 순환 참조 체크 ==========

    private boolean isDescendant(Long ancestorId, Long descendantId) {
        if (descendantId == null) return false;
        if (ancestorId.equals(descendantId)) return true;

        Optional<NoteFolder> folderOpt = noteFolderRepository.findById(descendantId);
        if (folderOpt.isEmpty()) return false;

        return isDescendant(ancestorId, folderOpt.get().getParentFolderId());
    }
}
