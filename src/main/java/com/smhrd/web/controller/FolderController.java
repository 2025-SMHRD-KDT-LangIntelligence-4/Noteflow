package com.smhrd.web.controller;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.repository.FolderRepository;
import com.smhrd.web.repository.UserRepository;
import com.smhrd.web.security.CustomUserDetails;
import com.smhrd.web.service.FileMetadataService;
import com.smhrd.web.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FileMetadataService fileMetadataService;

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
        result.put("folders", folderService.getFileFolderTree(userIdx));
        result.put("rootFiles", fileMetadataService.getRootFiles(userIdx));
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

    @PutMapping("/api/folders/{folderId}/move")
    @ResponseBody
    public Map<String, Object> moveFolder(
            @PathVariable String folderId,
            @RequestBody Map<String, String> payload,
            Authentication auth
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userIdx = ((CustomUserDetails) auth.getPrincipal()).getUserIdx();
            String targetFolderId = payload.get("targetFolderId");

            // 폴더 존재 확인
            Optional<Folder> folderOpt = folderRepository.findById(folderId);
            if (folderOpt.isEmpty()) {
                result.put("success", false);
                result.put("message", "폴더를 찾을 수 없습니다.");
                return result;
            }

            Folder folder = folderOpt.get();

            // 권한 확인
            if (!folder.getUserIdx().equals(userIdx)) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return result;
            }

            // 자기 자신으로 이동 불가
            if (folder.getId().equals(targetFolderId)) {
                result.put("success", false);
                result.put("message", "같은 폴더입니다.");
                return result;
            }

            // 타겟 폴더가 자신의 하위 폴더인지 확인
            if (targetFolderId != null && isDescendantFolder(folderId, targetFolderId)) {
                result.put("success", false);
                result.put("message", "하위 폴더로 이동할 수 없습니다.");
                return result;
            }

            // 타겟 폴더 존재 확인
            if (targetFolderId != null && !targetFolderId.isEmpty()) {
                Optional<Folder> targetOpt = folderRepository.findById(targetFolderId);
                if (targetOpt.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "대상 폴더를 찾을 수 없습니다.");
                    return result;
                }

                Folder targetFolder = targetOpt.get();
                if (!targetFolder.getUserIdx().equals(userIdx)) {
                    result.put("success", false);
                    result.put("message", "대상 폴더에 권한이 없습니다.");
                    return result;
                }
            }

            // 폴더 이동
            folder.setParentFolderId(targetFolderId);
            folderRepository.save(folder);

            result.put("success", true);
            result.put("message", "폴더가 이동되었습니다.");

        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "폴더 이동 중 오류 발생: " + e.getMessage());
        }

        return result;
    }

    // 순환 참조 방지용
    private boolean isDescendantFolder(String ancestorId, String descendantId) {
        if (descendantId == null || descendantId.isEmpty()) return false;
        if (ancestorId.equals(descendantId)) return true;

        Optional<Folder> folderOpt = folderRepository.findById(descendantId);
        if (folderOpt.isEmpty()) return false;

        Folder folder = folderOpt.get();
        return isDescendantFolder(ancestorId, folder.getParentFolderId());
    }
}
