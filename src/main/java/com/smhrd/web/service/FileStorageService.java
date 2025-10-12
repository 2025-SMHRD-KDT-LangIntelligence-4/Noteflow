package com.smhrd.web.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 파일 저장, 탐색, 미리보기 서비스
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final GridFSBucket gridFSBucket;

    // --------------------------
    // 파일 저장
    // --------------------------
    public String storeFile(MultipartFile file, String userId) throws IOException {
        String filename = file.getOriginalFilename();
        String storedFilename = generateStoredFilename(filename);

        Document metadata = new Document()
                .append("originalFilename", filename)
                .append("mimeType", file.getContentType())
                .append("size", file.getSize())
                .append("uploadedAt", Date.from(
                        LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()))
                .append("uploaderId", userId);  // 업로더 ID 저장

        GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);
        GridFSUploadStream uploadStream =
                gridFSBucket.openUploadStream(storedFilename, options);
        uploadStream.write(file.getBytes());
        uploadStream.close();

        return uploadStream.getObjectId().toString();
    }

    // --------------------------
    // 파일 다운로드
    // --------------------------
    public byte[] downloadFile(String mongoDocId) throws IOException {
        ObjectId id = new ObjectId(mongoDocId);
        GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(id);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = downloadStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        downloadStream.close();
        return output.toByteArray();
    }

    // --------------------------
    // 파일 삭제
    // --------------------------
    public boolean deleteFile(String mongoDocId) {
        try {
            gridFSBucket.delete(new ObjectId(mongoDocId));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --------------------------
    // 파일 트리 조회 (listFiles) 수정
    // --------------------------
    public List<FileInfo> listFiles() {
        GridFSFindIterable files = gridFSBucket.find();
        List<FileInfo> list = new ArrayList<>();
        for (GridFSFile gf : files) {
            Document md = gf.getMetadata();
            String uploaderIdValue = md != null ? md.getString("uploaderId") : null;
            list.add(new FileInfo(
                    gf.getObjectId().toString(),
                    gf.getFilename(),
                    md != null ? md.getString("originalFilename") : gf.getFilename(),
                    gf.getLength(),
                    md != null ? md.getString("mimeType") : null,
                    gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    uploaderIdValue  // use uploaderIdValue
            ));
        }
        return list;
    }

    // --------------------------
    // 파일 미리보기 (previewFile) 수정
    // --------------------------
    public FileInfo previewFile(String mongoDocId) {
        ObjectId id = new ObjectId(mongoDocId);
        GridFSFile gf = gridFSBucket.find(new Document("_id", id)).first();
        if (gf == null) return null;
        Document md = gf.getMetadata();
        String uploaderIdValue = md != null ? md.getString("uploaderId") : null;
        return new FileInfo(
                gf.getObjectId().toString(),
                gf.getFilename(),
                md != null ? md.getString("originalFilename") : gf.getFilename(),
                gf.getLength(),
                md != null ? md.getString("mimeType") : null,
                gf.getUploadDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                uploaderIdValue  // use uploaderIdValue
        );
    }

    // --------------------------
    // 유틸리티: 저장 파일명 생성
    // --------------------------
    private String generateStoredFilename(String originalFilename) {
        String ts = String.valueOf(System.currentTimeMillis());
        String base = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(0, originalFilename.lastIndexOf('.'))
                : originalFilename;
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        return base + "_" + ts + ext;
    }
    /**
     * 주어진 Mongo ID 목록의 파일을 ZIP으로 묶어 출력 스트림에 쓴다.
     *
     * @param ids      MongoDB GridFS ObjectId 문자열 목록
     * @param zos      ZipOutputStream (wrapped around HTTP response output stream)
     * @throws IOException
     */
    public void writeFilesAsZip(List<String> ids, ZipOutputStream zos) throws IOException {
        for (String idStr : ids) {
            ObjectId id = new ObjectId(idStr);
            // GridFS 파일 메타
            GridFSFile file = gridFSBucket.find(new Document("_id", id)).first();
            if (file == null) continue;

            String originalName = file.getMetadata().getString("originalFilename");
            // ZIP 엔트리에 파일명 추가 (인코딩 주의)
            String entryName = URLEncoder.encode(originalName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            zos.putNextEntry(new ZipEntry(entryName));

            // 파일 내용 스트림
            GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(id);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = downloadStream.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
            downloadStream.close();
            zos.closeEntry();
        }
        zos.finish();
    }
    // 미리보기 관련
    public String getFilePreview(String mongoDocId, String userId) {
        try {
            // 1. 파일 정보 및 권한 확인
            FileInfo fileInfo = previewFile(mongoDocId);
            if (fileInfo == null) {
                return "파일을 찾을 수 없습니다.";
            }

            if (!userId.equals(fileInfo.getUploaderId())) {
                return "접근 권한이 없습니다.";
            }

            // 2. 파일 타입별 미리보기 처리
            String mimeType = fileInfo.getMimeType();
            if (mimeType == null) {
                mimeType = detectMimeTypeFromFilename(fileInfo.getOriginalName());
            }

            return extractTextContent(mongoDocId, mimeType, fileInfo.getOriginalName());

        } catch (Exception e) {
            return "파일 미리보기 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // 파일에서 텍스트 추출
    private String extractTextContent(String mongoDocId, String mimeType, String filename) {
        try {
            byte[] fileData = downloadFile(mongoDocId);

            if (mimeType.contains("text/plain") || filename.endsWith(".txt")) {
                return extractPlainText(fileData);
            }
            else if (mimeType.contains("application/pdf") || filename.endsWith(".pdf")) {
                return extractPdfText(fileData);
            }
            else if (mimeType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || filename.endsWith(".docx")) {
                return extractDocxText(fileData);
            }
            else if (filename.endsWith(".md") || filename.endsWith(".markdown")) {
                return extractPlainText(fileData);
            }
            else if (mimeType.startsWith("image/")) {
                return "이미지 파일입니다: " + filename;
            }
            else {
                return "미리보기를 지원하지 않는 파일 형식입니다: " + mimeType;
            }

        } catch (Exception e) {
            return "파일 내용 추출 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    // 기본텍스트 추출
    private String extractPlainText(byte[] fileData) {
        try {
            String content = new String(fileData, StandardCharsets.UTF_8);

            // 미리보기용으로 길이 제한 (2000자)
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
            }

            return content;

        } catch (Exception e) {
            return "텍스트 파일을 읽을 수 없습니다: " + e.getMessage();
        }
    }

    // pdf 추출
    private String extractPdfText(byte[] fileData) {
        try {
            // [FIX] PDFBox 3.x 에서는 PDDocument.load() 대신 Loader.loadPDF()를 사용합니다.
            // byte[]를 직접 인자로 전달하여 더 간결하게 처리합니다.
            try (PDDocument document = Loader.loadPDF(fileData)) {

                PDFTextStripper pdfStripper = new PDFTextStripper();

                // 첫 3페이지만 미리보기
                pdfStripper.setStartPage(1);
                pdfStripper.setEndPage(Math.min(3, document.getNumberOfPages()));

                String text = pdfStripper.getText(document);

                if (text.length() > 2000) {
                    text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
                }

                return text.isEmpty() ? "PDF에서 텍스트를 추출할 수 없습니다." : text;
            }

        } catch (Exception e) {
            // 실제 운영 환경에서는 로깅 프레임워크(e.g., SLF4J) 사용을 권장합니다.
            System.err.println("PDF 파일 처리 중 오류 발생: " + e.getMessage());
            return "PDF 파일 처리 중 오류가 발생했습니다.";
        }
    }

    //docx 추출
    private String extractDocxText(byte[] fileData) {
        try {
            // Apache POI 사용 (pom.xml에 의존성 추가 필요)
            try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
                 XWPFDocument document = new XWPFDocument(bis)) {

                XWPFWordExtractor extractor = new XWPFWordExtractor(document);
                String text = extractor.getText();

                if (text.length() > 2000) {
                    text = text.substring(0, 2000) + "\n\n... (내용이 더 있습니다)";
                }

                extractor.close();
                return text.isEmpty() ? "DOCX에서 텍스트를 추출할 수 없습니다." : text;
            }

        } catch (Exception e) {
            return "DOCX 파일 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    //mime 타입 추출
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


    // 파일 정보 DTO
    @Getter
    @AllArgsConstructor
    public static class FileInfo {
        private final String id;
        private final String storedName;
        private final String originalName;
        private final long size;
        private final String mimeType;
        private final LocalDateTime uploadedAt;
        private final String uploaderId;

    }
}
