package com.smhrd.web.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.repository.FileMetadataRepository;
import com.smhrd.web.repository.FolderRepository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

/**
 * 파일 저장, 탐색, 미리보기, ZIP 다운로드 서비스
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    @Autowired
    private FolderRepository folderRepository;

    private final GridFSBucket gridFSBucket;
    private final FileMetadataRepository fileMetadataRepository;

    // --------------------------
    // 파일 저장
    // --------------------------
    public String storeFile(MultipartFile file, Long userIdx, String folderId) throws IOException {
        String filename = file.getOriginalFilename();
        String storedFilename = generateStoredFilename(filename);

        Document metadata = new Document()
            .append("originalFilename", filename)
            .append("mimeType", file.getContentType())
            .append("size", file.getSize())
            .append("uploadedAt", Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()))
            .append("uploaderIdx", userIdx);

        GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);

        ObjectId objectId;
        try (GridFSUploadStream uploadStream = gridFSBucket.openUploadStream(storedFilename, options)) {
            uploadStream.write(file.getBytes());
            objectId = uploadStream.getObjectId(); // ✅ 실제 Mongo ObjectId
        }

        // MongoDB 메타데이터 저장
        FileMetadata meta = FileMetadata.builder()
            .originalName(filename)
            .storedName(storedFilename)
            .fileSize(file.getSize())
            .mimeType(file.getContentType())
            .userIdx(userIdx)
            .folderId((folderId == null || folderId.isBlank()) ? null : folderId)
            .uploadDate(LocalDateTime.now())
            .gridfsId(objectId.toHexString()) // ✅ ObjectId를 문자열로 저장
            .build();

        fileMetadataRepository.save(meta);

        return objectId.toHexString(); // ✅ ObjectId 반환
    }

    private String generateStoredFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unknown";
        }

        String ext = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            ext = originalFilename.substring(dotIndex);
        }

        String uuid = UUID.randomUUID().toString();
        return uuid + ext;
    }

    

    // --------------------------
    // 파일 다운로드
    // --------------------------
    public byte[] downloadFile(String mongoDocId) throws IOException {
        ObjectId id = new ObjectId(mongoDocId);
        try (GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(id);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = downloadStream.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return output.toByteArray();
        }
    }

    // --------------------------
    // 파일 삭제
    // --------------------------
    public boolean deleteFile(String mongoDocId) {
        try {
            gridFSBucket.delete(new ObjectId(mongoDocId));
            fileMetadataRepository.findByGridfsId(mongoDocId)
                    .ifPresent(m -> fileMetadataRepository.deleteById(m.getId()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --------------------------
    // 파일 트리 조회
    // --------------------------
    public List<FileInfo> listFiles() {
        GridFSFindIterable files = gridFSBucket.find();
        List<FileInfo> list = new ArrayList<>();
        for (GridFSFile gf : files) {
            Document md = gf.getMetadata();
            Long uploaderIdxValue = md != null ? md.getLong("uploaderIdx") : null;
            list.add(new FileInfo(
                    gf.getObjectId().toString(),
                    gf.getFilename(),
                    md != null ? md.getString("originalFilename") : gf.getFilename(),
                    gf.getLength(),
                    md != null ? md.getString("mimeType") : null,
                    gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    uploaderIdxValue != null ? String.valueOf(uploaderIdxValue) : null
            ));
        }
        return list;
    }

    // --------------------------
    // 파일 미리보기
    // --------------------------
    public FileInfo previewFile(String mongoDocId) {
        ObjectId id = new ObjectId(mongoDocId);
        GridFSFile gf = gridFSBucket.find(new Document("_id", id)).first();
        if (gf == null) return null;
        Document md = gf.getMetadata();
        Long uploaderIdxValue = md != null ? md.getLong("uploaderIdx") : null;
        return new FileInfo(
                gf.getObjectId().toString(),
                gf.getFilename(),
                md != null ? md.getString("originalFilename") : gf.getFilename(),
                gf.getLength(),
                md != null ? md.getString("mimeType") : null,
                gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                uploaderIdxValue != null ? String.valueOf(uploaderIdxValue) : null
        );
    }

    // --------------------------
    // 파일 미리보기 권한 체크
    // --------------------------
    public String getFilePreview(String mongoDocId, Long userIdx) {
        try {
            FileInfo fileInfo = previewFile(mongoDocId);
            if (fileInfo == null) return "파일을 찾을 수 없습니다.";
            if (!String.valueOf(userIdx).equals(fileInfo.getUploaderIdx())) return "접근 권한이 없습니다.";

            String mimeType = fileInfo.getMimeType();
            if (mimeType == null) mimeType = detectMimeTypeFromFilename(fileInfo.getOriginalName());

            return extractTextContent(mongoDocId, mimeType, fileInfo.getOriginalName());
        } catch (Exception e) {
            return "파일 미리보기 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // --------------------------
    // ZIP 다운로드 지원
    // --------------------------
    public void writeFilesAsZip(List<String> fileIds, ZipOutputStream zos) throws IOException {
        for (String fileId : fileIds) {
            FileInfo fileInfo = previewFile(fileId);
            if (fileInfo == null) continue;

            byte[] data = downloadFile(fileId);
            ZipEntry entry = new ZipEntry(fileInfo.getOriginalName());
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
        }
    }

    // --------------------------
    // 내부 텍스트 추출 메서드
    // --------------------------
    private String extractTextContent(String mongoDocId, String mimeType, String filename) {
        try {
            byte[] fileData = downloadFile(mongoDocId);

            if (mimeType.contains("text/plain") || filename.endsWith(".txt") || filename.endsWith(".md") || filename.endsWith(".markdown"))
                return extractPlainText(fileData);
            else if (mimeType.contains("application/pdf") || filename.endsWith(".pdf"))
                return extractPdfText(fileData);
            else if (mimeType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document") || filename.endsWith(".docx"))
                return extractDocxText(fileData);
            else if (mimeType.startsWith("image/"))
                return "이미지 파일입니다: " + filename;
            else
                return "미리보기를 지원하지 않는 파일 형식입니다: " + mimeType;

        } catch (Exception e) {
            return "파일 내용 추출 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String extractPlainText(byte[] fileData) {
        try {
            String content = new String(fileData, StandardCharsets.UTF_8);
            if (content.length() > 2000) content = content.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
            return content;
        } catch (Exception e) {
            return "텍스트 파일을 읽을 수 없습니다: " + e.getMessage();
        }
    }

    private String extractPdfText(byte[] fileData) {
        try (PDDocument document = Loader.loadPDF(fileData)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(Math.min(3, document.getNumberOfPages()));
            String text = pdfStripper.getText(document);
            if (text.length() > 2000) text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
            return text.isEmpty() ? "PDF에서 텍스트를 추출할 수 없습니다." : text;
        } catch (Exception e) {
            System.err.println("PDF 처리 오류: " + e.getMessage());
            return "PDF 파일 처리 중 오류가 발생했습니다.";
        }
    }

    private String extractDocxText(byte[] fileData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            if (text.length() > 2000) text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
            return text.isEmpty() ? "DOCX에서 텍스트를 추출할 수 없습니다." : text;
        } catch (Exception e) {
            return "DOCX 파일 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private String detectMimeTypeFromFilename(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    // --------------------------
    // 파일 정보 DTO
    // --------------------------
    @Getter
    @AllArgsConstructor
    public static class FileInfo {
        private final String id;
        private final String storedName;
        private final String originalName;
        private final long size;
        private final String mimeType;
        private final LocalDateTime uploadedAt;
        private final String uploaderIdx; // long -> String
    }
    public String storeFile(MultipartFile file, Long userIdx) throws IOException {
        return storeFile(file, userIdx, null);
    }
}
