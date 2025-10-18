// src/main/java/com/smhrd/web/entity/NoteFolder.java
package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(
        name = "note_folders",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_note_folder_user_parent_name",
                columnNames = {"user_idx", "parent_folder_id", "folder_name"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "parent_folder_id")
    private Long parentFolderId;

    @Column(name = "folder_name", nullable = false, length = 255)
    private String folderName;

    @Column(name = "sort_order")
    @Builder.Default  // ✅ 추가
    private Integer sortOrder = 0;

    @Column(name = "status", length = 20)
    @Builder.Default  // ✅ 추가
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (sortOrder == null) sortOrder = 0;  // ✅ 안전장치
        if (status == null) status = "ACTIVE";  // ✅ 안전장치
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    private List<NoteFolder> subfolders = new ArrayList<>();
    @Transient
    private List<Note> notes = new ArrayList<>();

    public void addSubfolder(NoteFolder f) { this.subfolders.add(f); }
    public void addNote(Note n) { this.notes.add(n); }
}
