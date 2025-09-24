package com.smhrd.service;

import com.smhrd.model.entity.DocumentEntity;
import com.smhrd.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

// 비즈니스 로직 처리 (파일 저장, DB 저장, 파일 파싱 등)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    private final String uploadDir = "uploads/"; // 로컬 기본 경로

    public DocumentEntity saveFile(MultipartFile file) throws IOException {
        // 1. 파일명 가져오기
        String fileName = file.getOriginalFilename();

        // 2. 저장 경로 생성 (폴더가 없으면 생성)
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        // 3. 파일 로컬에 저장
        String filePath = uploadDir + fileName;
        file.transferTo(new File(filePath));

        // 4. DB 저장
        DocumentEntity document = DocumentEntity.builder()
                .fileName(fileName)
                .filePath(filePath)
                .uploadTime(LocalDateTime.now())
                .build();

        return documentRepository.save(document);
    }
}
