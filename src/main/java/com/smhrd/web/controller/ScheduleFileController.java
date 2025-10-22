package com.smhrd.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedule-files")
public class ScheduleFileController {

    // (application.properties에 업로드 경로 설정 필요: file.upload-dir=./uploads)
    @Value("${file.upload-dir:./uploads}") // 기본값 설정
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("File is empty", HttpStatus.BAD_REQUEST);
        }

        try {
            // (보안: 파일명 정제 및 UUID 사용)
            String originalFileName = file.getOriginalFilename();
            String extension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String storedFileName = UUID.randomUUID().toString() + extension;

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                 Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // (WebConfig에서 /uploads/** 경로를 정적 리소스로 매핑해야 함)
            String fileDownloadUri = "/uploads/" + storedFileName; 

            // 파일 원본명과 저장된 경로(URI)를 반환
            return ResponseEntity.ok(Map.of(
                "fileName", originalFileName, // 사용자가 볼 원본 파일명
                "filePath", fileDownloadUri   // DB에 저장될 경로
            ));

        } catch (IOException e) {
            System.err.println("Could not store file: " + e.getMessage());
            return new ResponseEntity<>("Could not store file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // (TODO: 파일 삭제 API - /api/files/delete)
}