package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.service.UnifiedFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unified")
@RequiredArgsConstructor
public class UnifiedFolderController {

    private final UnifiedFolderService unifiedFolderService;

    /**
     * 노트 폴더 트리 구조 조회 (노트 포함)
     */
    @GetMapping("/notes/tree")
    public ResponseEntity<Map<String, Object>> getNoteTree(Authentication auth) {
        String userId = auth.getName();

        Map<String, Object> result = new HashMap<>();
        result.put("folders", unifiedFolderService.getNoteFolderTree(userId));
        result.put("rootNotes", unifiedFolderService.getRootNotes(userId));

        return ResponseEntity.ok(result);
    }

    /**
     * 파일 폴더 트리 구조 조회 (파일 포함)
     */
    @GetMapping("/files/tree")
    public ResponseEntity<Map<String, Object>> getFileTree(Authentication auth) {
        String userId = auth.getName();

        Map<String, Object> result = new HashMap<>();
        List<Folder> fileTree = unifiedFolderService.getFileFolderTree(userId);

        // 루트 파일들 추출
        List<FileMetadata> rootFiles = fileTree.isEmpty() ?
                unifiedFolderService.getRootFiles(userId) : List.of();

        result.put("folders", fileTree);
        result.put("rootFiles", rootFiles);

        return ResponseEntity.ok(result);
    }

    /**
     * 노트 폴더 생성
     */
    @PostMapping("/notes/folder")
    public ResponseEntity<Map<String, Object>> createNoteFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) Long parentFolderId,
            Authentication auth) {

        try {
            Long folderId = unifiedFolderService.createNoteFolder(auth.getName(), folderName, parentFolderId);

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

    /**
     * 노트를 폴더로 이동
     */
    @PutMapping("/notes/move")
    public ResponseEntity<Map<String, Object>> moveNote(
            @RequestParam Long noteId,
            @RequestParam(required = false) Long targetFolderId,
            Authentication auth) {

        try {
            unifiedFolderService.moveNoteToFolder(auth.getName(), noteId, targetFolderId);

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

    /**
     * 노트 폴더 삭제
     */
    @DeleteMapping("/notes/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteNoteFolder(
            @PathVariable Long folderId,
            Authentication auth) {

        try {
            unifiedFolderService.deleteNoteFolder(auth.getName(), folderId);

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
}