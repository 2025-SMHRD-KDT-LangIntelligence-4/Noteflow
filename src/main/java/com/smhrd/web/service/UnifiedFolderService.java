package com.smhrd.web.service;

import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnifiedFolderService {

    private final NoteFolderRepository noteFolderRepository;
    private final NoteRepository noteRepository;
    private final NoteTagRepository noteTagRepository;

    // ========================================
    // NoteFolder 트리 조회
    // ========================================

    public List<NoteFolder> getNoteFolderTree(Long userIdx) {
        List<NoteFolder> allFolders = noteFolderRepository
                .findByUserIdxOrderByFolderNameAsc(userIdx);  // status 제거
        List<Note> allNotes = noteRepository
                .findByUser_UserIdxAndStatusOrderByCreatedAtDesc(userIdx, "ACTIVE");
        // ✅ 각 노트에 태그 로드
        allNotes.forEach(note -> {
            List<NoteTag> noteTags = noteTagRepository.findAllByNote(note);
            System.out.println("Note: " + note.getTitle() + " has " + noteTags.size() + " tags");  // ✅ 로그
            List<Tag> tags = noteTags.stream()
                    .map(NoteTag::getTag)
                    .collect(Collectors.toList());
            note.setTags(tags);
        });
        // null 제외하고 그룹화
        Map<Long, List<NoteFolder>> foldersByParent = allFolders.stream()
                .filter(f -> f.getParentFolderId() != null)
                .collect(Collectors.groupingBy(NoteFolder::getParentFolderId));

        Map<Long, List<Note>> notesByFolder = allNotes.stream()
                .filter(n -> n.getFolderId() != null)
                .collect(Collectors.groupingBy(Note::getFolderId));

        // 루트 폴더 직접 필터링
        List<NoteFolder> rootFolders = allFolders.stream()
                .filter(f -> f.getParentFolderId() == null)
                .collect(Collectors.toList());

        // 트리 구조 생성
        rootFolders.forEach(root -> buildNoteFolderTree(root, foldersByParent, notesByFolder));

        return rootFolders;
    }

    private void buildNoteFolderTree(NoteFolder folder,
                                     Map<Long, List<NoteFolder>> foldersByParent,
                                     Map<Long, List<Note>> notesByFolder) {
        List<NoteFolder> subs = foldersByParent.getOrDefault(folder.getFolderId(), new ArrayList<>());
        subs.forEach(sub -> {
            folder.addSubfolder(sub);
            buildNoteFolderTree(sub, foldersByParent, notesByFolder);
        });

        List<Note> notes = notesByFolder.getOrDefault(folder.getFolderId(), new ArrayList<>());
        notes.forEach(folder::addNote);
    }

    public List<Note> getRootNotes(Long userIdx) {
        List<Note> notes = noteRepository
                .findByUser_UserIdxAndFolderIdIsNullAndStatusOrderByCreatedAtDesc(userIdx, "ACTIVE");

        // 루트 노트태그 로드
        notes.forEach(note -> {
            List<NoteTag> noteTags = noteTagRepository.findAllByNote(note);
            List<Tag> tags = noteTags.stream()
                    .map(NoteTag::getTag)
                    .collect(Collectors.toList());
            note.setTags(tags);
        });

        return notes;
    }

    // ========================================
    // NoteFolder 생성/삭제/이름변경
    // ========================================

    @Transactional
    public Long createNoteFolder(Long userIdx, String folderName, Long parentFolderId) {
        if (noteFolderRepository.existsByUserIdxAndFolderNameAndParentFolderId(
                userIdx, folderName, parentFolderId)) {
            throw new IllegalArgumentException("이미 존재하는 폴더입니다.");
        }

        NoteFolder folder = NoteFolder.builder()
                .userIdx(userIdx)
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .sortOrder(0)
                .status("ACTIVE")
                .build();

        return noteFolderRepository.save(folder).getFolderId();
    }

    @Transactional
    public void deleteNoteFolder(Long userIdx, Long folderId) {
        NoteFolder folder = noteFolderRepository.findByFolderIdAndUserIdx(folderId, userIdx)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        deleteSubNoteFoldersRecursively(userIdx, folderId);

        List<Note> notes = noteRepository
                .findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(userIdx, folderId, "ACTIVE");
        notes.forEach(n -> {
            n.setFolderId(null);
            noteRepository.save(n);
        });

        noteFolderRepository.deleteByFolderIdAndUserIdx(folderId, userIdx);
    }

    private void deleteSubNoteFoldersRecursively(Long userIdx, Long parentFolderId) {
        List<NoteFolder> subs = noteFolderRepository
                .findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentFolderId);

        for (NoteFolder sub : subs) {
            deleteSubNoteFoldersRecursively(userIdx, sub.getFolderId());

            List<Note> notes = noteRepository
                    .findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(
                            userIdx, sub.getFolderId(), "ACTIVE");
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

        if (!folder.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("폴더 이름은 필수입니다.");
        }

        folder.setFolderName(newName.trim());
        noteFolderRepository.save(folder);
    }

    // ========================================
    // NoteFolder 이동 (병합 지원)
    // ========================================

    @Transactional
    public void moveNoteFolderWithMerge(Long userIdx, Long folderId, Long targetParentId) {
        NoteFolder folder = noteFolderRepository.findByFolderIdAndUserIdx(folderId, userIdx)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        Optional<NoteFolder> existingFolder = noteFolderRepository
                .findByUserIdxAndParentFolderIdAndFolderName(userIdx, targetParentId, folder.getFolderName());

        if (existingFolder.isPresent() && !existingFolder.get().getFolderId().equals(folderId)) {
            NoteFolder target = existingFolder.get();
            mergeNoteFolders(folder, target, userIdx);
        } else {
            folder.setParentFolderId(targetParentId);
            folder.setUpdatedAt(LocalDateTime.now());
            noteFolderRepository.save(folder);
        }
    }

    private void mergeNoteFolders(NoteFolder source, NoteFolder target, Long userIdx) {
        List<NoteFolder> subfolders = noteFolderRepository
                .findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, source.getFolderId());

        for (NoteFolder sub : subfolders) {
            Optional<NoteFolder> existingSubFolder = noteFolderRepository
                    .findByUserIdxAndParentFolderIdAndFolderName(userIdx, target.getFolderId(), sub.getFolderName());

            if (existingSubFolder.isPresent()) {
                mergeNoteFolders(sub, existingSubFolder.get(), userIdx);
            } else {
                sub.setParentFolderId(target.getFolderId());
                sub.setUpdatedAt(LocalDateTime.now());
                noteFolderRepository.save(sub);
            }
        }

        List<Note> notes = noteRepository
                .findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(userIdx, source.getFolderId(), "ACTIVE");
        notes.forEach(note -> {
            note.setFolderId(target.getFolderId());
            noteRepository.save(note);
        });

        noteFolderRepository.delete(source);
    }

    // ========================================
    // Note 이동
    // ========================================

    @Transactional
    public void moveNoteToFolder(Long userIdx, Long noteId, Long targetFolderId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("노트를 찾을 수 없습니다."));

        if (!note.getUser().getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        if (targetFolderId != null) {
            noteFolderRepository.findByFolderIdAndUserIdx(targetFolderId, userIdx)
                    .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        }

        note.setFolderId(targetFolderId);
        noteRepository.save(note);
    }
}
