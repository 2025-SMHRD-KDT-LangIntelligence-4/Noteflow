package com.smhrd.web.repository;

import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserOrderByCreatedAtDesc(User user);

    List<Note> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    // JPQL 쿼리 수정: userId → userIdx(Long)
    @Query("SELECT n FROM Note n WHERE n.user.userIdx = :userIdx AND n.status = 'ACTIVE' AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Note> searchByUserAndKeyword(@Param("userIdx") Long userIdx,
                                      @Param("keyword") String keyword);

    List<Note> findByUser_UserIdxAndFolderIdAndStatusOrderByCreatedAtDesc(Long userIdx, Long folderId, String status);

    List<Note> findByUser_UserIdxAndStatusOrderByCreatedAtDesc(Long userIdx, String status);

    List<Note> findByUser_UserIdxAndFolderIdIsNullAndStatusOrderByCreatedAtDesc(Long userIdx, String status);

    @Modifying
    @Query("UPDATE Note n SET n.folderId = :folderId WHERE n.noteIdx = :noteIdx")
    void updateNoteFolderId(@Param("noteIdx") Long noteIdx, @Param("folderId") Long folderId);


    @Modifying
    @Query("UPDATE Note n SET n.sourceId = :sourceId WHERE n.noteIdx = :noteIdx")
    void updateNoteSourceId(@Param("noteIdx") Long noteIdx, @Param("sourceId") String sourceId);

}
