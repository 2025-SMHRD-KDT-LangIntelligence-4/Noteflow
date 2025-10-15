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

    @Field("user_idx") // 변경: user_id → user_idx
    private Long userIdx;  // 변경: String → Long

    @Field("folder_id")
    private String folderId; // null이면 루트 레벨

    @Field("upload_date")
    private LocalDateTime uploadDate;

    @Field("gridfs_id")
    private String gridfsId; // GridFS ObjectId

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
}
