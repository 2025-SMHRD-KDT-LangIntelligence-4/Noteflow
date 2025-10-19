package com.smhrd.web.service;

import com.smhrd.web.entity.*;
import com.smhrd.web.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository folderRepository;

    public List<FileMetadata> getRootFiles(Long userIdx) {
        return fileMetadataRepository.findRootFilesByUserIdx(userIdx);
    }

    @Transactional
    public void moveFile(Long userIdx, String fileId, String targetFolderId) {
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
