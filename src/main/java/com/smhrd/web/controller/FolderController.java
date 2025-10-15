package com.smhrd.web.controller;

import com.smhrd.web.repository.UserRepository;
import com.smhrd.web.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final UserRepository userRepository;

    // --------------------------
    // 폴더 트리 조회
    // --------------------------
    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> getFolderTree(Authentication auth) {
        String username = auth.getName();
        Long userIdx = userRepository.findByUserId(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username))
                .getUserIdx();

        Map<String, Object> result = new HashMap<>();
        result.put("folders", folderService.getFolderTree(userIdx));
        result.put("rootFiles", folderService.getRootFiles(userIdx));
        return ResponseEntity.ok(result);
    }

    // --------------------------
    // 새 폴더 생성
    // --------------------------
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestParam String folderName,
            @RequestParam(required = false) String parentFolderId,
            Authentication auth) {

        try {
            String username = auth.getName();
            Long userIdx = userRepository.findByUserId(username)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username))
                    .getUserIdx();

            String folderId = folderService.createFolder(userIdx, folderName, parentFolderId);

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

    // --------------------------
    // 폴더 이름 변경
    // --------------------------
    @PutMapping("/{folderId}/rename")
    public ResponseEntity<Map<String, Object>> renameFolder(
            @PathVariable String folderId,
            @RequestParam String newName,
            Authentication auth) {

        try {
            String username = auth.getName();
            Long userIdx = userRepository.findByUserId(username)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username))
                    .getUserIdx();

            folderService.renameFolder(userIdx, folderId, newName);

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

    // --------------------------
    // 폴더 삭제
    // --------------------------
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteFolder(
            @PathVariable String folderId,
            Authentication auth) {

        try {
            String username = auth.getName();
            Long userIdx = userRepository.findByUserId(username)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username))
                    .getUserIdx();

            folderService.deleteFolder(userIdx, folderId);

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

    // --------------------------
    // 파일을 폴더로 이동
    // --------------------------
    @PutMapping("/move-file")
    public ResponseEntity<Map<String, Object>> moveFile(
            @RequestParam String fileId,
            @RequestParam(required = false) String targetFolderId,
            Authentication auth) {

        try {
            String username = auth.getName();
            Long userIdx = userRepository.findByUserId(username)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + username))
                    .getUserIdx();

            // [수정] userIdx 기반으로 FolderService 호출
            folderService.moveFileToFolder(userIdx, fileId, targetFolderId);

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
