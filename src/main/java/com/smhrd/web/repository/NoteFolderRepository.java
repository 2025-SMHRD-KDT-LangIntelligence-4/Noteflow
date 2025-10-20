package com.smhrd.web.repository;

import com.smhrd.web.entity.NoteFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NoteFolderRepository extends JpaRepository<NoteFolder, Long> {

    // ========== 트리 조회용 ==========
    List<NoteFolder> findByUserIdxOrderByFolderNameAsc(Long userIdx);

    List<NoteFolder> findByUserIdxAndParentFolderIdOrderByFolderNameAsc(Long userIdx, Long parentFolderId);

    // ✅ 추가 - LLMUnifiedService에서 사용
    List<NoteFolder> findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(Long userIdx);

    // ========== 폴더 관리용 ==========
    Optional<NoteFolder> findByFolderIdAndUserIdx(Long folderId, Long userIdx);

    Optional<NoteFolder> findByUserIdxAndParentFolderIdAndFolderName(
            Long userIdx, Long parentFolderId, String folderName);

    boolean existsByUserIdxAndFolderNameAndParentFolderId(
            Long userIdx, String folderName, Long parentFolderId);

    void deleteByFolderIdAndUserIdx(Long folderId, Long userIdx);

    Optional<NoteFolder> findRootByUserIdxAndFolderName(Long userIdx, String folderName);

}
