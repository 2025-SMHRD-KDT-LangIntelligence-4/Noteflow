package com.smhrd.web.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Document(collection = "files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    @Id
    private String id;

    @Field("original_name")
    private String originalName;

    @Field("stored_name")
    private String storedName;

    @Field("file_size")
    private Long fileSize;

    @Field("mime_type")
    private String mimeType;

    @Field("user_idx")
    private Long userIdx;

    @Field("folder_id")
    private String folderId; // null이면 루트 레벨

    @Field("upload_date")
    private LocalDateTime uploadDate;

    @Field("gridfs_id")
    private String gridfsId; // GridFS ObjectId

    // ✅ 추가: 파일 상태 필드
    @Field("status")
    @Builder.Default
    private String status = "ACTIVE";  // ACTIVE, EXPIRED, DELETED

    // ✅ 추가: 삭제 시점 기록
    @Field("deleted_at")
    private LocalDateTime deletedAt;

    // 파일 타입별 아이콘
    public String getFileIcon() {
        if (originalName == null) return "📄";

        String ext = originalName.toLowerCase();
        if (ext.endsWith(".pdf")) return "📕";
        if (ext.endsWith(".docx") || ext.endsWith(".doc")) return "📘";
        if (ext.endsWith(".txt")) return "📄";
        if (ext.endsWith(".md")) return "📝";
        if (ext.endsWith(".jpg") || ext.endsWith(".png") || ext.endsWith(".gif")) return "🖼️";
        return "📄";
    }

    // ✅ 파일 만료 여부 확인
    public boolean isExpired() {
        return "EXPIRED".equals(this.status);
    }
}
