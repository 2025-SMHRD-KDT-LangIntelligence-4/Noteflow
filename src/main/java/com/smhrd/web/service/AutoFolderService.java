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
    public Long createOrFindFolder(Long userIdx, CategoryResult categoryResult) { // userId → userIdx
        if (categoryResult.getMatchedCategory() == null) {
            return null;
        }
        String[] path = categoryResult.getSuggestedFolderPath().split("/");
        Long parentId = null;
        for (String name : path) {
            parentId = findOrCreate(userIdx, name, parentId);
        }
        return parentId;
    }

    private Long findOrCreate(Long userIdx, String folderName, Long parentId) { // userId → userIdx
        List<NoteFolder> siblings = (parentId == null)
                ? noteFolderRepository.findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(userIdx)
                : noteFolderRepository.findByUserIdxAndParentFolderIdOrderByFolderNameAsc(userIdx, parentId);

        return siblings.stream()
                .filter(f -> f.getFolderName().equals(folderName))
                .findFirst()
                .map(NoteFolder::getFolderId)
                .orElseGet(() -> createNewFolder(userIdx, folderName, parentId));
    }

    private Long createNewFolder(Long userIdx, String folderName, Long parentFolderId) { // userId → userIdx
        NoteFolder folder = NoteFolder.builder()
                .userIdx(userIdx) // String → Long
                .folderName(folderName)
                .parentFolderId(parentFolderId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return noteFolderRepository.save(folder).getFolderId();
    }
}
