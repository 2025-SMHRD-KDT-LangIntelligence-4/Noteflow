package com.smhrd.web.repository;

import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserOrderByCreatedAtDesc(User user);
    List<Note> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    @Query("SELECT n FROM Note n WHERE n.user.userId = :userId AND n.status = 'ACTIVE' AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Note> searchByUserAndKeyword(@Param("userId") String userId,
                                      @Param("keyword") String keyword);
}