package com.smhrd.web.repository;

import com.smhrd.web.entity.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {

    // 생성일 기준 정렬
    List<Folder> findByUserIdxOrderByCreatedAtAsc(Long userIdx);

    List<Folder> findByParentFolderId(String parentFolderId);

    Optional<Folder> findByUserIdxAndParentFolderIdAndFolderName(
            Long userIdx, String parentFolderId, String folderName);

    Optional<Folder> findByUserIdxAndFolderNameAndParentFolderIdIsNull(
            Long userIdx,
            String folderName
    );

    // 특정 부모 아래 폴더 찾기
    Optional<Folder> findByUserIdxAndFolderNameAndParentFolderId(
            Long userIdx,
            String folderName,
            String parentFolderId
    );
}

