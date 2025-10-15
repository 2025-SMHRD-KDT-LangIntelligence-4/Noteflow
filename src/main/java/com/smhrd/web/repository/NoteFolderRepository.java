// src/main/java/com/smhrd/web/repository/NoteFolderRepository.java
package com.smhrd.web.repository;

import com.smhrd.web.entity.NoteFolder;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteFolderRepository extends JpaRepository<NoteFolder, Long> {

    // 전체 폴더
    List<NoteFolder> findByUserIdxOrderByFolderNameAsc(long userIdx);

    // 루트 레벨 폴더
    List<NoteFolder> findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(long userIdx);

    // 특정 부모 하위 폴더
    List<NoteFolder> findByUserIdxAndParentFolderIdOrderByFolderNameAsc(long userIdx, Long parentFolderId);

    // 단일 폴더(권한 확인)
    Optional<NoteFolder> findByFolderIdAndUserIdx(Long folderId, long userIdx);

    // 중복 확인
    boolean existsByUserIdxAndFolderNameAndParentFolderId(long userIdx, String folderName, Long parentFolderId);

    // 폴더 삭제
    void deleteByFolderIdAndUserIdx(Long folderId, long userIdx);


    @Query("SELECT f FROM NoteFolder f " +
            "WHERE f.userIdx = :userIdx AND f.parentFolderId IS NULL AND f.folderName = :folderName")
    Optional<NoteFolder> findRootByUserIdxAndFolderName(@Param("userIdx") Long userIdx,
                                                        @Param("folderName") String folderName);

    // 특정 부모 아래에서 이름으로 찾기
    Optional<NoteFolder> findByUserIdxAndParentFolderIdAndFolderName(
            Long userIdx, Long parentFolderId, String folderName);
}


