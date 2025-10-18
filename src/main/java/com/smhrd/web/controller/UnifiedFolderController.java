package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.security.CustomUserDetails;
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

    // ─────────────────────────────────────────────────────────────────────
    // 내부 유틸: 인증 체크 + 401 응답 생성
    // ─────────────────────────────────────────────────────────────────────
    private boolean isAnonymous(Authentication auth) {
        return auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        Map<String, Object> body = Map.of("success", false, "message", "UNAUTHORIZED");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private Long getUserIdx(Authentication auth) {
        return ((com.smhrd.web.security.CustomUserDetails) auth.getPrincipal()).getUserIdx();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 노트 폴더 트리 구조 조회 (노트 포함)
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/notes/tree")
    public ResponseEntity<Map<String, Object>> getNoteTree(Authentication auth) {
        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        Map<String, Object> result = new HashMap<>();
        result.put("folders", unifiedFolderService.getNoteFolderTree(userIdx));
        result.put("rootNotes", unifiedFolderService.getRootNotes(userIdx));
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 파일 폴더 트리 구조 조회 (파일 포함)
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/files/tree")
    public ResponseEntity<Map<String, Object>> getFileTree(Authentication auth) {
        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        Map<String, Object> result = new HashMap<>();
        // 폴더 트리
        List<Folder> fileTree = unifiedFolderService.getFileFolderTree(userIdx);
        result.put("folders", fileTree);

        // ✅ 루트 파일은 폴더 유무와 상관 없이 항상 내려줍니다.
        List<FileMetadata> rootFiles = unifiedFolderService.getRootFiles(userIdx);
        result.put("rootFiles", rootFiles);

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 노트 폴더 생성
    // ─────────────────────────────────────────────────────────────────────
    @PostMapping("/notes/folder")
    public ResponseEntity<Map<String, Object>> createNoteFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) Long parentFolderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            Long folderId = unifiedFolderService.createNoteFolder(userIdx, folderName, parentFolderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("folderId", folderId);
            response.put("message", "노트 폴더가 생성되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 노트를 폴더로 이동 (targetFolderId 없으면 루트로)
    // ─────────────────────────────────────────────────────────────────────
    @PutMapping("/notes/move")
    public ResponseEntity<Map<String, Object>> moveNote(
            @RequestParam Long noteId,
            @RequestParam(required = false) Long targetFolderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            unifiedFolderService.moveNoteToFolder(userIdx, noteId, targetFolderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "노트가 이동되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 노트 폴더 삭제
    // ─────────────────────────────────────────────────────────────────────
    @DeleteMapping("/notes/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteNoteFolder(
            @PathVariable Long folderId,
            Authentication auth) {

        if (isAnonymous(auth)) return unauthorized();
        Long userIdx = getUserIdx(auth);

        try {
            unifiedFolderService.deleteNoteFolder(userIdx, folderId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "노트 폴더가 삭제되었습니다.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    @PutMapping("/note-folders/{folderId}/move")
    @ResponseBody
    public Map<String, Object> moveNoteFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, Long> payload,
            Authentication auth
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
            Long targetFolderId = payload.get("targetFolderId");

            // 폴더 존재 확인
            Optional<NoteFolder> folderOpt = noteFolderRepository.findById(folderId);
            if (folderOpt.isEmpty()) {
                result.put("success", false);
                result.put("message", "폴더를 찾을 수 없습니다.");
                return result;
            }

            NoteFolder folder = folderOpt.get();

            // 권한 확인
            if (!folder.getUserIdx().equals(userIdx)) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return result;
            }

            // 자기 자신으로 이동 불가
            if (folder.getFolderId().equals(targetFolderId)) {
                result.put("success", false);
                result.put("message", "같은 폴더입니다.");
                return result;
            }

            // 타겟 폴더가 자신의 하위 폴더인지 확인 (순환 참조 방지)
            if (targetFolderId != null && isDescendant(folderId, targetFolderId)) {
                result.put("success", false);
                result.put("message", "하위 폴더로 이동할 수 없습니다.");
                return result;
            }

            // 타겟 폴더 존재 확인
            if (targetFolderId != null) {
                Optional<NoteFolder> targetOpt = noteFolderRepository.findById(targetFolderId);
                if (targetOpt.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "대상 폴더를 찾을 수 없습니다.");
                    return result;
                }

                NoteFolder targetFolder = targetOpt.get();
                if (!targetFolder.getUserIdx().equals(userIdx)) {
                    result.put("success", false);
                    result.put("message", "대상 폴더에 권한이 없습니다.");
                    return result;
                }
            }

            // 폴더 이동
            folder.setParentFolderId(targetFolderId);
            noteFolderRepository.save(folder);

            result.put("success", true);
            result.put("message", "폴더가 이동되었습니다.");

        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "폴더 이동 중 오류 발생: " + e.getMessage());
        }

        return result;
    }

    // 순환 참조 방지용 헬퍼 메소드
    private boolean isDescendant(Long ancestorId, Long descendantId) {
        if (descendantId == null) return false;
        if (ancestorId.equals(descendantId)) return true;

        Optional<NoteFolder> folderOpt = noteFolderRepository.findById(descendantId);
        if (folderOpt.isEmpty()) return false;

        NoteFolder folder = folderOpt.get();
        return isDescendant(ancestorId, folder.getParentFolderId());
    }
    @PutMapping("/notes/folder/{folderId}/rename")
    public ResponseEntity<Map<String, Object>> renameNoteFolder(
            @PathVariable("folderId") Long folderId,
            @RequestParam("newName") String newName,
            Authentication auth) {

        Map<String, Object> response = new HashMap<>();
        try {
            if (isAnonymous(auth)) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }
            Long userIdx = getUserIdx(auth);
            unifiedFolderService.renameNoteFolder(userIdx, folderId, newName);

            response.put("success", true);
            response.put("message", "폴더 이름이 변경되었습니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            response.put("success", false);
            response.put("message", iae.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "서버 에러: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
