package com.smhrd.web.repository;

import com.smhrd.web.entity.Attachment;
import com.smhrd.web.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByNoteAndStatusOrderByCreatedAtDesc(Note note, String status);
    Optional<Attachment> findByMongoDocId(String mongoDocId);
    int countByNoteAndStatus(Note note, String status);
}