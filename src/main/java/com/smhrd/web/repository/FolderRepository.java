package com.smhrd.web.repository;

import com.smhrd.web.entity.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {

    // 루트 폴더 조회
    List<Folder> findByUserIdxAndParentFolderIdIsNullOrderByFolderNameAsc(Long userIdx);

    // 특정 부모 폴더 하위 폴더 조회
    List<Folder> findByUserIdxAndParentFolderIdOrderByFolderNameAsc(Long userIdx, String parentFolderId);

    // 모든 폴더 조회
    List<Folder> findByUserIdxOrderByFolderNameAsc(Long userIdx);

    // 단일 폴더 조회
    Optional<Folder> findByIdAndUserIdx(String id, Long userIdx);

    // 폴더 존재 여부 확인
    boolean existsByUserIdxAndFolderNameAndParentFolderId(Long userIdx, String folderName, String parentFolderId);

    // 폴더 삭제
    void deleteByIdAndUserIdx(String id, Long userIdx);

    // 생성일 기준 정렬
    List<Folder> findByUserIdxOrderByCreatedAtAsc(Long userIdx);
    List<Folder> findByParentFolderId(String parentFolderId);

    Optional<Folder> findByUserIdxAndParentFolderIdAndFolderName(
            Long userIdx, String parentFolderId, String folderName);
}

