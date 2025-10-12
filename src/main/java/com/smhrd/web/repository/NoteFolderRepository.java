// src/main/java/com/smhrd/web/repository/NoteFolderRepository.java
package com.smhrd.web.repository;

import com.smhrd.web.entity.NoteFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteFolderRepository extends JpaRepository<NoteFolder, Long> {

    // 전체 폴더
    List<NoteFolder> findByUserIdxOrderByFolderNameAsc(String userIdx);

    // 루트 레벨 폴더
    List<NoteFolder> findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(String userIdx);

    // 특정 부모 하위 폴더
    List<NoteFolder> findByUserIdxAndParentFolderIdOrderByFolderNameAsc(String userIdx, Long parentFolderId);

    // 단일 폴더(권한 확인)
    Optional<NoteFolder> findByFolderIdAndUserIdx(Long folderId, String userIdx);

    // 중복 확인
    boolean existsByUserIdxAndFolderNameAndParentFolderId(String userIdx, String folderName, Long parentFolderId);

    // 폴더 삭제
    void deleteByFolderIdAndUserIdx(Long folderId, String userIdx);
}
