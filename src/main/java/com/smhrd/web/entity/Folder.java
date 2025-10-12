package com.smhrd.web.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Folder {

    @Id
    private String id;

    @Field("folder_name")
    private String folderName;

    @Field("parent_folder_id")
    private String parentFolderId; // null이면 루트 폴더

    @Field("user_id")
    private String userId;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    // 트리 구조를 위한 가상 필드 (DB에 저장되지 않음)
    private List<Folder> subfolders = new ArrayList<>();
    private List<FileMetadata> files = new ArrayList<>();

    public boolean isRoot() {
        return parentFolderId == null;
    }

    public void addSubfolder(Folder subfolder) {
        this.subfolders.add(subfolder);
    }

    public void addFile(FileMetadata file) {
        this.files.add(file);
    }
}