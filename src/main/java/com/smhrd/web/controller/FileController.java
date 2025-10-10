package com.smhrd.web.controller;

import com.smhrd.web.service.FileStorageService;
import com.smhrd.web.service.FileStorageService.FileInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // --------------------------
    // 파일 업로드 (uploaderId 기록)
    // --------------------------
    @PostMapping("/api/files/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
                                          Authentication auth) {
        String userId = auth.getName();
        Map<String, Object> result = new HashMap<>();
        try {
            String mongoId = fileStorageService.storeFile(file, userId);
            result.put("success", true);
            result.put("mongoId", mongoId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // --------------------------
    // 파일 다운로드 (본인만)
    // --------------------------
    @GetMapping("/api/files/download/{id}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String id,
                                               Authentication auth) throws IOException {
        FileInfo info = fileStorageService.previewFile(id);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        String userId = auth.getName();
        if (!userId.equals(info.getUploaderId())) {
            return ResponseEntity.status(403).build();
        }
        byte[] data = fileStorageService.downloadFile(id);
        String encodedName = URLEncoder.encode(info.getOriginalName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedName);
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    // --------------------------
    // 파일 목록 조회 (본인 파일만)
    // --------------------------
    @GetMapping("/api/files/list")
    public List<FileInfo> listFiles(Authentication auth) {
        String userId = auth.getName();
        return fileStorageService.listFiles().stream()
                .filter(info -> userId.equals(info.getUploaderId()))
                .collect(Collectors.toList());
    }

    // --------------------------
    // 다중 파일 ZIP 다운로드
    // --------------------------
    @PostMapping("/api/files/download-zip")
    public void downloadZip(@RequestBody List<String> ids,
                            Authentication auth,
                            HttpServletResponse response) throws IOException {
        String userId = auth.getName();
        List<String> authorized = fileStorageService.listFiles().stream()
                .filter(info -> userId.equals(info.getUploaderId()) && ids.contains(info.getId()))
                .map(FileInfo::getId)
                .collect(Collectors.toList());

        String zipName = URLEncoder.encode("files.zip", StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + zipName);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            fileStorageService.writeFilesAsZip(authorized, zos);
        }
    }
    @DeleteMapping("/api/files/delete/{id}")
    public Map<String, Object> deleteFile(@PathVariable String id, Authentication auth) {
        String userId = auth.getName();
        Map<String, Object> result = new HashMap<>();
        try {
            FileInfo info = fileStorageService.previewFile(id);
            if (info == null || !userId.equals(info.getUploaderId())) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return result;
            }
            boolean deleted = fileStorageService.deleteFile(id);
            result.put("success", deleted);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
