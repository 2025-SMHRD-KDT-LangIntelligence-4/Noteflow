// src/main/java/com/smhrd/web/service/UnifiedFolderService.java
package com.smhrd.web.service;

import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.FileMetadataRepository;
import com.smhrd.web.repository.NoteFolderRepository;
import com.smhrd.web.repository.NoteRepository;
import com.smhrd.web.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnifiedFolderService {

    private final NoteFolderRepository noteFolderRepository;
    private final NoteRepository noteRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository mongoFolderRepository;

    public List<NoteFolder> getNoteFolderTree(String userId) {
        List<NoteFolder> allFolders = noteFolderRepository.findByUserIdxOrderByFolderNameAsc(userId);
        List<Note> allNotes = noteRepository
                .findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");

        Map<Long, List<NoteFolder>> foldersByParent = allFolders.stream()
                .collect(Collectors.groupingBy(f -> f.getParentFolderId() != null ? f.getParentFolderId() : -1L));

        Map<Long, List<Note>> notesByFolder = allNotes.stream()
                .collect(Collectors.groupingBy(n -> n.getFolderId() != null ? n.getFolderId() : -1L));

        List<NoteFolder> rootFolders = foldersByParent.getOrDefault(-1L, List.of());
        return rootFolders.stream()
                .peek(root -> buildNoteFolderTree(root, foldersByParent, notesByFolder))
                .collect(Collectors.toList());
    }

    private void buildNoteFolderTree(NoteFolder folder,
                                     Map<Long, List<NoteFolder>> foldersByParent,
                                     Map<Long, List<Note>> notesByFolder) {
        List<NoteFolder> subs = foldersByParent.get(folder.getFolderId());
        if (subs != null) {
            subs.forEach(sub -> {
                // 필요 시 NoteFolder에 subfolders/notes 보조 컬렉션 필드와 add 메서드 추가
                buildNoteFolderTree(sub, foldersByParent, notesByFolder);
            });
        }
        List<Note> notes = notesByFolder.get(folder.getFolderId());
        if (notes != null) {
            // 필요 시 folder에 addNote 적용
        }
    }

    public List<Note> getRootNotes(String userId) {
        return noteRepository.findByUser_UserIdAndFolderIdIsNullAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
    }

    public List<FileMetadata> getRootFiles(String userId) {
        return fileMetadataRepository.findByUserIdAndFolderIdIsNullOrderByOriginalNameAsc(userId);
    }

    public List<Folder> getFileFolderTree(String userId) {
        List<Folder> allFolders = mongoFolderRepository.findByUserIdOrderByFolderNameAsc(userId);
        List<FileMetadata> allFiles = fileMetadataRepository.findByUserIdOrderByUploadDateDesc(userId);
        return buildFileFolderTree(allFolders, allFiles);
    }

    private List<Folder> buildFileFolderTree(List<Folder> allFolders, List<FileMetadata> allFiles) {
        Map<String, List<Folder>> foldersByParent = allFolders.stream()
                .collect(Collectors.groupingBy(f -> f.getParentFolderId() != null ? f.getParentFolderId() : "ROOT"));

        Map<String, List<FileMetadata>> filesByFolder = allFiles.stream()
                .collect(Collectors.groupingBy(m -> m.getFolderId() != null ? m.getFolderId() : "ROOT"));

        List<Folder> roots = foldersByParent.getOrDefault("ROOT", List.of());
        return roots.stream()
                .peek(root -> buildFolderTree(root, foldersByParent, filesByFolder))
                .collect(Collectors.toList());
    }

    private void buildFolderTree(Folder folder,
                                 Map<String, List<Folder>> foldersByParent,
                                 Map<String, List<FileMetadata>> filesByFolder) {
        List<Folder> subs = foldersByParent.get(folder.getId());
        if (subs != null) {
            subs.forEach(sub -> {
                // folder.addSubfolder(sub);
                buildFolderTree(sub, foldersByParent, filesByFolder);
            });
        }
        List<FileMetadata> files = filesByFolder.get(folder.getId());
        if (files != null) {
            // files.forEach(folder::addFile);
        }
    }

    @Transactional
    public Long createNoteFolder(String userId, String folderName, Long parentFolderId) {
        if (noteFolderRepository.existsByUserIdxAndFolderNameAndParentFolderId(userId, folderName, parentFolderId)) {
            throw new IllegalArgumentException("같은 이름의 폴더가 이미 존재합니다.");
        }
        NoteFolder folder = NoteFolder.builder()
                .userIdx(userId) // 통일: String
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .build();
        return noteFolderRepository.save(folder).getFolderId();
    }

    @Transactional
    public void moveNoteToFolder(String userId, Long noteId, Long targetFolderId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));
        if (!note.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        if (targetFolderId != null) {
            noteFolderRepository.findByFolderIdAndUserIdx(targetFolderId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("대상 폴더를 찾을 수 없습니다."));
        }
        note.setFolderId(targetFolderId);
        noteRepository.save(note);
    }

    @Transactional
    public void deleteNoteFolder(String userId, Long folderId) {
        NoteFolder folder = noteFolderRepository.findByFolderIdAndUserIdx(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        deleteSubNoteFoldersRecursively(userId, folderId);
        List<Note> notes = noteRepository
                .findByUser_UserIdAndFolderIdAndStatusOrderByCreatedAtDesc(userId, folderId, "ACTIVE");
        notes.forEach(n -> {
            n.setFolderId(null);
            noteRepository.save(n);
        });
        noteFolderRepository.deleteByFolderIdAndUserIdx(folder.getFolderId(), userId);
    }

    private void deleteSubNoteFoldersRecursively(String userId, Long parentFolderId) {
        List<NoteFolder> subs = noteFolderRepository
                .findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userId, parentFolderId);
        for (NoteFolder sub : subs) {
            deleteSubNoteFoldersRecursively(userId, sub.getFolderId());
            List<Note> notes = noteRepository
                    .findByUser_UserIdAndFolderIdAndStatusOrderByCreatedAtDesc(userId, sub.getFolderId(), "ACTIVE");
            notes.forEach(n -> {
                n.setFolderId(null);
                noteRepository.save(n);
            });
            noteFolderRepository.deleteByFolderIdAndUserIdx(sub.getFolderId(), userId);
        }
    }
}
