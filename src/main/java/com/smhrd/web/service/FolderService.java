package com.smhrd.web.service;

import com.smhrd.web.entity.Folder;
import com.smhrd.web.entity.FileMetadata;
import com.smhrd.web.repository.FolderRepository;
import com.smhrd.web.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileMetadataRepository fileMetadataRepository;

    /**
     * 사용자의 폴더 트리 구조 조회
     */
    public List<Folder> getFolderTree(String userId) {
        // 1. 모든 폴더와 파일 조회
        List<Folder> allFolders = folderRepository.findByUserIdOrderByFolderNameAsc(userId);
        List<FileMetadata> allFiles = fileMetadataRepository.findByUserIdOrderByUploadDateDesc(userId);

        // 2. 폴더 ID로 그룹화
        Map<String, List<Folder>> foldersByParent = allFolders.stream()
                .collect(Collectors.groupingBy(folder ->
                        folder.getParentFolderId() != null ? folder.getParentFolderId() : "ROOT"));

        // 3. 각 폴더에 파일 배치
        Map<String, List<FileMetadata>> filesByFolder = allFiles.stream()
                .collect(Collectors.groupingBy(file ->
                        file.getFolderId() != null ? file.getFolderId() : "ROOT"));

        // 4. 루트 폴더들 가져오기
        List<Folder> rootFolders = foldersByParent.get("ROOT");
        if (rootFolders == null) return List.of();

        // 5. 각 루트 폴더에 하위 구조 빌드
        return rootFolders.stream()
                .peek(folder -> buildFolderTree(folder, foldersByParent, filesByFolder))
                .collect(Collectors.toList());
    }

    /**
     * 재귀적으로 폴더 트리 구조 빌드
     */
    private void buildFolderTree(Folder folder,
                                 Map<String, List<Folder>> foldersByParent,
                                 Map<String, List<FileMetadata>> filesByFolder) {

        // 하위 폴더들 추가
        List<Folder> subfolders = foldersByParent.get(folder.getId());
        if (subfolders != null) {
            subfolders.forEach(subfolder -> {
                folder.addSubfolder(subfolder);
                buildFolderTree(subfolder, foldersByParent, filesByFolder); // 재귀
            });
        }

        // 폴더 내 파일들 추가
        List<FileMetadata> files = filesByFolder.get(folder.getId());
        if (files != null) {
            files.forEach(folder::addFile);
        }
    }

    /**
     * 루트 레벨 파일들 (폴더에 속하지 않은 파일들)
     */
    public List<FileMetadata> getRootFiles(String userId) {
        return fileMetadataRepository.findByUserIdAndFolderIdIsNullOrderByOriginalNameAsc(userId);
    }

    /**
     * 새 폴더 생성
     */
    public String createFolder(String userId, String folderName, String parentFolderId) {
        // 중복 검사
        if (folderRepository.existsByUserIdAndFolderNameAndParentFolderId(userId, folderName, parentFolderId)) {
            throw new IllegalArgumentException("같은 이름의 폴더가 이미 존재합니다.");
        }

        Folder folder = Folder.builder()
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return folderRepository.save(folder).getId();
    }

    /**
     * 폴더 이름 변경
     */
    public void renameFolder(String userId, String folderId, String newName) {
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        // 같은 부모 폴더 내에서 이름 중복 검사
        if (folderRepository.existsByUserIdAndFolderNameAndParentFolderId(
                userId, newName, folder.getParentFolderId())) {
            throw new IllegalArgumentException("같은 이름의 폴더가 이미 존재합니다.");
        }

        folder.setFolderName(newName);
        folder.setUpdatedAt(LocalDateTime.now());
        folderRepository.save(folder);
    }

    /**
     * 폴더 삭제 (하위 폴더와 파일 포함)
     */
    public void deleteFolder(String userId, String folderId) {
        Folder folder = folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        // 하위 폴더들 재귀적 삭제
        deleteSubfoldersRecursively(userId, folderId);

        // 폴더 내 파일들 삭제
        List<FileMetadata> files = fileMetadataRepository.findByUserIdAndFolderIdOrderByOriginalNameAsc(userId, folderId);
        files.forEach(file -> fileMetadataRepository.deleteByIdAndUserId(file.getId(), userId));

        // 폴더 자체 삭제
        folderRepository.deleteByIdAndUserId(folderId, userId);
    }

    private void deleteSubfoldersRecursively(String userId, String parentFolderId) {
        List<Folder> subfolders = folderRepository.findByUserIdAndParentFolderIdOrderByFolderNameAsc(userId, parentFolderId);

        for (Folder subfolder : subfolders) {
            // 재귀적으로 하위 폴더 삭제
            deleteSubfoldersRecursively(userId, subfolder.getId());

            // 폴더 내 파일들 삭제
            List<FileMetadata> files = fileMetadataRepository.findByUserIdAndFolderIdOrderByOriginalNameAsc(userId, subfolder.getId());
            files.forEach(file -> fileMetadataRepository.deleteByIdAndUserId(file.getId(), userId));

            // 폴더 삭제
            folderRepository.deleteByIdAndUserId(subfolder.getId(), userId);
        }
    }

    /**
     * 파일을 폴더로 이동
     */
    public void moveFileToFolder(String userId, String fileId, String targetFolderId) {
        FileMetadata file = fileMetadataRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다."));

        // 대상 폴더 존재 확인 (null이면 루트로 이동)
        if (targetFolderId != null) {
            folderRepository.findByIdAndUserId(targetFolderId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("대상 폴더를 찾을 수 없습니다."));
        }

        file.setFolderId(targetFolderId);
        fileMetadataRepository.save(file);
    }
}