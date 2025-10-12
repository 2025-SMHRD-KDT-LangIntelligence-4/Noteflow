package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /**
     * 폴더 트리 구조 조회 (파일 포함)
     */
    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> getFolderTree(Authentication auth) {
        String userId = auth.getName();

        Map<String, Object> result = new HashMap<>();
        result.put("folders", folderService.getFolderTree(userId));
        result.put("rootFiles", folderService.getRootFiles(userId));

        return ResponseEntity.ok(result);
    }

    /**
     * 새 폴더 생성
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) String parentFolderId,
            Authentication auth) {

        try {
            String folderId = folderService.createFolder(auth.getName(), folderName, parentFolderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("folderId", folderId);
            response.put("message", "폴더가 생성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 폴더 이름 변경
     */
    @PutMapping("/{folderId}/rename")
    public ResponseEntity<Map<String, Object>> renameFolder(
            @PathVariable String folderId,
            @RequestParam String newName,
            Authentication auth) {

        try {
            folderService.renameFolder(auth.getName(), folderId, newName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "폴더 이름이 변경되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 폴더 삭제
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteFolder(
            @PathVariable String folderId,
            Authentication auth) {

        try {
            folderService.deleteFolder(auth.getName(), folderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "폴더가 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 파일을 폴더로 이동
     */
    @PutMapping("/move-file")
    public ResponseEntity<Map<String, Object>> moveFile(
            @RequestParam String fileId,
            @RequestParam(required = false) String targetFolderId,
            Authentication auth) {

        try {
            folderService.moveFileToFolder(auth.getName(), fileId, targetFolderId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "파일이 이동되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}