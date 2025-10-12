// src/main/java/com/smhrd/web/service/AutoFolderService.java
package com.smhrd.web.service;

import com.smhrd.web.dto.CategoryResult;
import com.smhrd.web.entity.NoteFolder;
import com.smhrd.web.repository.NoteFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutoFolderService {

    private final NoteFolderRepository noteFolderRepository;

    @Transactional
    public Long createOrFindFolder(String userId, CategoryResult categoryResult) {
        if (categoryResult.getMatchedCategory() == null) {
            return null;
        }
        String[] path = categoryResult.getSuggestedFolderPath().split("/");
        Long parentId = null;
        for (String name : path) {
            parentId = findOrCreate(userId, name, parentId);
        }
        return parentId;
    }

    private Long findOrCreate(String userId, String folderName, Long parentId) {
        List<NoteFolder> siblings = (parentId == null)
                ? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userId)
                : noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userId, parentId);

        return siblings.stream()
                .filter(f -> f.getFolderName().equals(folderName))
                .findFirst()
                .map(NoteFolder::getFolderId)
                .orElseGet(() -> createNewFolder(userId, folderName, parentId));
    }

    private Long createNewFolder(String userId, String folderName, Long parentFolderId) {
        NoteFolder folder = NoteFolder.builder()
                .userIdx(userId) // 통일: String
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return noteFolderRepository.save(folder).getFolderId();
    }
}
