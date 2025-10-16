package com.smhrd.web.repository;

import com.smhrd.web.entity.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    List<FileMetadata> findByUserIdxAndFolderIdIsNullOrderByOriginalNameAsc(Long userIdx);

    List<FileMetadata> findByUserIdxAndFolderIdOrderByOriginalNameAsc(Long userIdx, String folderId);

    List<FileMetadata> findByUserIdxOrderByUploadDateDesc(Long userIdx);

    Optional<FileMetadata> findByIdAndUserIdx(String id, Long userIdx);
    
    Optional<FileMetadata> findByGridfsId(String gridfsId);

    void deleteByIdAndUserIdx(String id, Long userIdx);

    long countByFolderId(String folderId);



    // 루트 파일 조회
    @Query(value = "{ 'userIdx': ?0, $or: [ { 'folderId': null }, { 'folderId': { $exists: false } }, { 'folderId': '' } ] }",
           sort  = "{ 'uploadDate': -1 }")
    List<FileMetadata> findRootFilesByUserIdx(Long userIdx);
}
