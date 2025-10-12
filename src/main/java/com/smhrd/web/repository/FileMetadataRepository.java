package com.smhrd.web.repository;

import com.smhrd.web.entity.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {

    List<FileMetadata> findByUserIdAndFolderIdIsNullOrderByOriginalNameAsc(String userId);

    List<FileMetadata> findByUserIdAndFolderIdOrderByOriginalNameAsc(String userId, String folderId);

    List<FileMetadata> findByUserIdOrderByUploadDateDesc(String userId);

    Optional<FileMetadata> findByIdAndUserId(String id, String userId);

    void deleteByIdAndUserId(String id, String userId);

    long countByFolderId(String folderId);

}