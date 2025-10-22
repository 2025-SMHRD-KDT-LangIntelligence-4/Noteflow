package com.smhrd.web.controller;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;

@RestController
@RequestMapping("/api/video")
public class VideoStreamController {

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private GridFSBucket gridFSBucket;

    /**
     * 영상 스트리밍 (Range Request 지원)
     * @param fileId GridFS 파일 ID
     * @param request HTTP 요청
     * @return 영상 스트림
     */
    @GetMapping("/stream/{fileId}")
    public void streamVideo(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        try {
            System.out.println("========================================");
            System.out.println("[VideoStream] 요청 fileId: " + fileId);

            ObjectId objectId = new ObjectId(fileId);

            GridFSFile gridFSFile = gridFsTemplate.findOne(
                    new Query(Criteria.where("_id").is(objectId))
            );

            if (gridFSFile == null) {
                System.out.println("[VideoStream] ✗ 파일 없음");
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            System.out.println("[VideoStream] ✓ 파일 찾음: " + gridFSFile.getFilename());

            long fileSize = gridFSFile.getLength();
            String rangeHeader = request.getHeader(HttpHeaders.RANGE);

            System.out.println("[VideoStream] 파일 크기: " + fileSize + " bytes");
            System.out.println("[VideoStream] Range: " + rangeHeader);

            // Range Request 처리
            long rangeStart = 0;
            long rangeEnd = fileSize - 1;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.substring(6).split("-");
                rangeStart = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    rangeEnd = Long.parseLong(ranges[1]);
                }
            }

            long contentLength = rangeEnd - rangeStart + 1;

            System.out.println("[VideoStream] 전송 범위: " + rangeStart + "-" + rangeEnd + " (" + contentLength + " bytes)");

            // Response 헤더 설정
            response.setContentType("video/mp4");
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

            if (rangeHeader != null) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        String.format("bytes %d-%d/%d", rangeStart, rangeEnd, fileSize));
                response.setContentLengthLong(contentLength);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentLengthLong(fileSize);
            }

            // ⭐ GridFS 스트리밍 (청크 단위)
            GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(objectId);
            downloadStream.skip(rangeStart);

            byte[] buffer = new byte[8192]; // 8KB 버퍼
            long bytesToRead = contentLength;
            long totalBytesRead = 0;

            OutputStream outputStream = response.getOutputStream();

            while (bytesToRead > 0) {
                int bytesRead = downloadStream.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead));
                if (bytesRead == -1) {
                    break;
                }
                outputStream.write(buffer, 0, bytesRead);
                bytesToRead -= bytesRead;
                totalBytesRead += bytesRead;
            }

            outputStream.flush();
            downloadStream.close();

            System.out.println("[VideoStream] ✓ 전송 완료: " + totalBytesRead + " bytes");
            System.out.println("========================================");

        } catch (Exception e) {
            System.out.println("[VideoStream] ✗ 에러: " + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    /**
     * 강의 ID로 영상 정보 조회
     */
    @GetMapping("/info/{lecIdx}")
    public ResponseEntity<?> getVideoInfo(@PathVariable Long lecIdx) {
        // lectures 테이블에서 video_file_id 조회
        // LectureRepository 사용

        return ResponseEntity.ok().build();
    }
}
