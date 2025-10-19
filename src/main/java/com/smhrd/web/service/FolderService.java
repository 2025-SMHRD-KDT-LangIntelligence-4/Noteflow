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
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileMetadataRepository fileMetadataRepository;

    // ========================================
    // Folder 트리 조회
    // ========================================

    public List<Folder> getFileFolderTree(Long userIdx) {
        List<Folder> allFolders = folderRepository.findByUserIdxOrderByCreatedAtAsc(userIdx);
        List<FileMetadata> allFiles = fileMetadataRepository.findByUserIdxOrderByUploadDateDesc(userIdx);

        Map<String, List<Folder>> foldersByParent = allFolders.stream()
                .filter(f -> f.getParentFolderId() != null)
                .collect(Collectors.groupingBy(Folder::getParentFolderId));

        Map<String, List<FileMetadata>> filesByFolder = allFiles.stream()
                .filter(f -> f.getFolderId() != null && !f.getFolderId().isBlank())
                .collect(Collectors.groupingBy(FileMetadata::getFolderId));

        List<Folder> roots = allFolders.stream()
                .filter(f -> f.getParentFolderId() == null)
                .collect(Collectors.toList());

        roots.forEach(root -> buildFolderTree(root, foldersByParent, filesByFolder));

        return roots;
    }

    private void buildFolderTree(Folder folder,
                                 Map<String, List<Folder>> foldersByParent,
                                 Map<String, List<FileMetadata>> filesByFolder) {
        List<Folder> subs = foldersByParent.getOrDefault(folder.getId(), new ArrayList<>());
        subs.forEach(sub -> {
            folder.addSubfolder(sub);
            buildFolderTree(sub, foldersByParent, filesByFolder);
        });

        List<FileMetadata> files = filesByFolder.getOrDefault(folder.getId(), new ArrayList<>());
        files.forEach(folder::addFile);
    }

    // ========================================
    // Folder 생성/삭제/이름변경
    // ========================================

    @Transactional
    public String createFolder(Long userIdx, String folderName, String parentFolderId) {
        Folder folder = new Folder();
        folder.setUserIdx(userIdx);
        folder.setFolderName(folderName);
        folder.setParentFolderId(parentFolderId);
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());

        return folderRepository.save(folder).getId();
    }

    @Transactional
    public void renameFolder(Long userIdx, String folderId, String newName) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        if (!folder.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        folder.setFolderName(newName);
        folder.setUpdatedAt(LocalDateTime.now());
        folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(Long userIdx, String folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        if (!folder.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        // 하위 폴더 재귀 삭제
        deleteFolderRecursively(folderId);
    }

    private void deleteFolderRecursively(String folderId) {
        List<Folder> subfolders = folderRepository.findByParentFolderId(folderId);
        subfolders.forEach(sub -> deleteFolderRecursively(sub.getId()));

        // 폴더 내 파일 삭제
        List<FileMetadata> files = fileMetadataRepository.findByFolderId(folderId);
        fileMetadataRepository.deleteAll(files);

        // 폴더 삭제
        folderRepository.deleteById(folderId);
    }

    // ========================================
    // Folder 이동 (병합 지원)
    // ========================================

    @Transactional
    public void moveFolder(Long userIdx, String folderId, String targetParentId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        if (!folder.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        Optional<Folder> existingFolder = folderRepository
                .findByUserIdxAndParentFolderIdAndFolderName(userIdx, targetParentId, folder.getFolderName());

        if (existingFolder.isPresent() && !existingFolder.get().getId().equals(folderId)) {
            Folder target = existingFolder.get();
            mergeFolders(folder, target, userIdx);
        } else {
            folder.setParentFolderId(targetParentId);
            folder.setUpdatedAt(LocalDateTime.now());
            folderRepository.save(folder);
        }
    }

    private void mergeFolders(Folder source, Folder target, Long userIdx) {
        List<Folder> subfolders = folderRepository.findByParentFolderId(source.getId());

        for (Folder sub : subfolders) {
            Optional<Folder> existingSubFolder = folderRepository
                    .findByUserIdxAndParentFolderIdAndFolderName(userIdx, target.getId(), sub.getFolderName());

            if (existingSubFolder.isPresent()) {
                mergeFolders(sub, existingSubFolder.get(), userIdx);
            } else {
                sub.setParentFolderId(target.getId());
                sub.setUpdatedAt(LocalDateTime.now());
                folderRepository.save(sub);
            }
        }

        List<FileMetadata> files = fileMetadataRepository.findByFolderId(source.getId());
        files.forEach(file -> {
            file.setFolderId(target.getId());
            fileMetadataRepository.save(file);
        });

        folderRepository.delete(source);
    }

    // ========================================
    // 파일 이동 (FolderController에서 사용)
    // ========================================

    @Transactional
    public void moveFileToFolder(Long userIdx, String fileId, String targetFolderId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));

        if (!file.getUserIdx().equals(userIdx)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        if (targetFolderId != null && !targetFolderId.isEmpty()) {
            Folder targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new IllegalArgumentException("대상 폴더를 찾을 수 없습니다."));

            if (!targetFolder.getUserIdx().equals(userIdx)) {
                throw new IllegalArgumentException("대상 폴더에 권한이 없습니다.");
            }
        }

        file.setFolderId(targetFolderId);
        fileMetadataRepository.save(file);
    }
}
