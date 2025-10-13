package com.smhrd.web.repository;

import com.smhrd.web.entity.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {

    List<Folder> findByUserIdAndParentFolderIdIsNullOrderByFolderNameAsc(String userId);

    List<Folder> findByUserIdAndParentFolderIdOrderByFolderNameAsc(String userId, String parentFolderId);

    List<Folder> findByUserIdOrderByFolderNameAsc(String userId);

    Optional<Folder> findByIdAndUserId(String id, String userId);

    boolean existsByUserIdAndFolderNameAndParentFolderId(String userId, String folderName, String parentFolderId);

    void deleteByIdAndUserId(String id, String userId);

	List<Folder> findByUserIdOrderByCreatedAtAsc(String userId);

}

