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
    private final FolderRepository folderRepository;

    // --------------------------
    // NoteFolder 트리 조회
    // --------------------------
    public List<NoteFolder> getNoteFolderTree(Long userIdx) {
        List<NoteFolder> allFolders = noteFolderRepository.findByUserIdxOrderByFolderNameAsc(userIdx);
        List<Note> allNotes = noteRepository.findByUser_UserIdxAndStatusOrderByCreatedAtDesc(userIdx, "ACTIVE");

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
                folder.addSubfolder(sub);
                buildNoteFolderTree(sub, foldersByParent, notesByFolder);
            });
        }
        List<Note> notes = notesByFolder.get(folder.getFolderId());
        if (notes != null) {
            notes.forEach(folder::addNote);
        }
    }

    public List<Note> getRootNotes(Long userIdx) {
        return noteRepository.findByUser_UserIdxAndFolderIdIsNullAndStatusOrderByCreatedAtDesc(userIdx, "ACTIVE");
    }

    public List<FileMetadata> getRootFiles(Long userIdx) {
        return fileMetadataRepository.findRootFilesByUserIdx(userIdx);
    }

    // --------------------------
    // Folder 트리 조회
    // --------------------------
    public List<Folder> getFileFolderTree(Long userIdx) {
        List<Folder> allFolders = folderRepository.findByUserIdxOrderByCreatedAtAsc(userIdx);
        Map<String, List<Folder>> foldersByParent = allFolders.stream()
                .collect(Collectors.groupingBy(f -> Objects.toString(f.getParentFolderId(), "ROOT")));

        List<FileMetadata> allFiles = fileMetadataRepository.findByUserIdxOrderByUploadDateDesc(userIdx);
        Map<String, List<FileMetadata>> filesByFolder = allFiles.stream()
                .filter(f -> f.getFolderId() != null && !f.getFolderId().isBlank())
                .collect(Collectors.groupingBy(FileMetadata::getFolderId));

        List<Folder> roots = foldersByParent.getOrDefault("ROOT", List.of());
        for (Folder root : roots) {
            buildFolderTree(root, foldersByParent, filesByFolder);
        }
        return roots;
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
        List<Folder> subs = foldersByParent.getOrDefault(folder.getId(), List.of());
        for (Folder sub : subs) {
            folder.addSubfolder(sub);
            buildFolderTree(sub, foldersByParent, filesByFolder);
        }
        List<FileMetadata> files = filesByFolder.get(folder.getId());
        if (files != null) files.forEach(folder::addFile);
    }

    // --------------------------
    // NoteFolder CRUD
    // --------------------------
    @Transactional
    public Long createNoteFolder(Long userIdx, String folderName, Long parentFolderId) {
        if (noteFolderRepository.existsByUserIdxAndFolderNameAndParentFolderId(userIdx, folderName, parentFolderId)) {
            throw new IllegalArgumentException("같은 이름의 폴더가 이미 존재합니다.");
        }
        NoteFolder folder = NoteFolder.builder()
                .userIdx(userIdx)
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .build();
        return noteFolderRepository.save(folder).getFolderId();
    }

    @Transactional
    public void moveNoteToFolder(Long userIdx, Long noteId, Long targetFolderId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));
        if (!note.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        if (targetFolderId != null) {
            noteFolderRepository.findByFolderIdAndUserIdx(targetFolderId, userIdx)
                    .orElseThrow(() -> new IllegalArgumentException("대상 폴더를 찾을 수 없습니다."));
        }
        note.setFolderId(targetFolderId);
        noteRepository.save(note);
    }

    @Transactional
    public void deleteNoteFolder(Long userIdx, Long folderId) {
        NoteFolder folder = noteFolderRepository.findByFolderIdAndUserIdx(folderId, userIdx)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        deleteSubNoteFoldersRecursively(userIdx, folderId);

        List<Note> notes = noteRepository.findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(userIdx, folderId, "ACTIVE");
        notes.forEach(n -> {
            n.setFolderId(null);
            noteRepository.save(n);
        });

        noteFolderRepository.deleteByFolderIdAndUserIdx(folder.getFolderId(), userIdx);
    }

    private void deleteSubNoteFoldersRecursively(Long userIdx, Long parentFolderId) {
        List<NoteFolder> subs = noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentFolderId);
        for (NoteFolder sub : subs) {
            deleteSubNoteFoldersRecursively(userIdx, sub.getFolderId());
            List<Note> notes = noteRepository.findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(userIdx, sub.getFolderId(), "ACTIVE");
            notes.forEach(n -> {
                n.setFolderId(null);
                noteRepository.save(n);
            });
            noteFolderRepository.deleteByFolderIdAndUserIdx(sub.getFolderId(), userIdx);
        }
    }

    @Transactional
    public void renameNoteFolder(Long userIdx, Long folderId, String newName) {
        NoteFolder folder = noteFolderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        if (folder.getUserIdx() == null || !folder.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("새 폴더명은 비어 있을 수 없습니다.");
        }
        folder.setFolderName(newName.trim());
        noteFolderRepository.save(folder);
    }

    // --------------------------
    // 파일 트리 조회 (폴더 + 루트 파일)
    // --------------------------
    public Map<String, Object> getFilesTree(Long userIdx) {
        Map<String,Object> result = new HashMap<>();
        result.put("folders", getFileFolderTree(userIdx));
        result.put("rootFiles", fileMetadataRepository.findRootFilesByUserIdx(userIdx));
        return result;
    }
}
