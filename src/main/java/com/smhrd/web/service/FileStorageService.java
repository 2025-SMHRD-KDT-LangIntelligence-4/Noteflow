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
