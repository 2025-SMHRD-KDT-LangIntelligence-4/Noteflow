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
    private final FolderRepository folderRepository;

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
            	folder.addSubfolder(sub);
                buildNoteFolderTree(sub, foldersByParent, notesByFolder);
            });
        }
        List<Note> notes = notesByFolder.get(folder.getFolderId());
        if (notes != null) {
        	notes.forEach(folder::addNote); 
        }
    }

    public List<Note> getRootNotes(String userId) {
        return noteRepository.findByUser_UserIdAndFolderIdIsNullAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
    }


	public List<FileMetadata> getRootFiles(String userId) {
	   return fileMetadataRepository.findRootFiles(userId); 
	 }



public List<Folder> getFileFolderTree(String userId) {
        // 1) 모든 폴더를 parentFolderId 기준으로 그룹핑 (null → "ROOT")
        List<Folder> allFolders = folderRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<String, List<Folder>> foldersByParent = allFolders.stream()
            .collect(Collectors.groupingBy(f -> Objects.toString(f.getParentFolderId(), "ROOT")));

        // 2) 모든 파일을 folderId 기준으로 그룹핑 (루트 파일은 제외; 루트는 별도로 내려감)
        List<FileMetadata> allFiles = fileMetadataRepository.findByUserIdOrderByUploadDateDesc(userId);
        Map<String, List<FileMetadata>> filesByFolder = allFiles.stream()
            .filter(f -> f.getFolderId() != null && !f.getFolderId().isBlank())
            .collect(Collectors.groupingBy(FileMetadata::getFolderId));

        // 3) 루트 폴더들부터 재귀 빌드
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
	        // 하위 폴더 연결
	        List<Folder> subs = foldersByParent.getOrDefault(folder.getId(), List.of());
	        for (Folder sub : subs) {
	            folder.addSubfolder(sub);              // Folder에 보조 필드/메서드가 있어야 함
	            buildFolderTree(sub, foldersByParent, filesByFolder);
	        }
	        // 하위 파일 연결
	        List<FileMetadata> files = filesByFolder.get(folder.getId());
	        if (files != null) files.forEach(folder::addFile);  
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
    


	public Map<String,Object> getFilesTree(String userId) {
	    Map<String,Object> result = new HashMap<>();
	    // 폴더 트리(재귀 빌드된 List<Folder>)를 반환하는 공개 메서드
	    result.put("folders", getFileFolderTree(userId));
	    // 루트 파일은 항상 채워서 내려줌
	    result.put("rootFiles", fileMetadataRepository.findRootFiles(userId));
	    return result;
	}
	 @Transactional
	    public void renameNoteFolder(String userId, Long folderId, String newName) {
	        NoteFolder folder = noteFolderRepository.findById(folderId)
	                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
	        // 사용자 검증(예시) - NoteFolder에 getUserId()가 존재한다고 가정
	        if (folder.getUserIdx() == null || !folder.getUserIdx().equals(userId)) {
	            throw new IllegalArgumentException("권한이 없습니다.");
	        }
	        if (newName == null || newName.trim().isEmpty()) {
	            throw new IllegalArgumentException("새 폴더명은 비어 있을 수 없습니다.");
	        }
	        folder.setFolderName(newName.trim());
	        noteFolderRepository.save(folder);
	    }

}
