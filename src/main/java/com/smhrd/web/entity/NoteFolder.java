// src/main/java/com/smhrd/web/entity/NoteFolder.java
package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "note_folders")
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
    private String userIdx;

    @Column(name = "folder_name", nullable = false, length = 100)
    private String folderName;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    @JsonIgnore  
    private NoteFolder parentFolder;
    
    @Column(name = "parent_folder_id", insertable = false, updatable = false)
    private Long parentFolderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 트리 응답 임시 필드 
    @Transient
    private List<NoteFolder> subfolders = new ArrayList<>();
    @Transient
    private List<Note> notes = new ArrayList<>();

    public void addSubfolder(NoteFolder f) { this.subfolders.add(f); }
    public void addNote(Note n) { this.notes.add(n); }


}
