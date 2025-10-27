package com.smhrd.web.service;

import com.smhrd.web.entity.Note;
import com.smhrd.web.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationService {

    private final NoteRepository noteRepository;
    private final MongoTemplate mongoTemplate;

    @Transactional(readOnly = true)
    public void migrateAllNotesToMongoDB() {
        log.info("=== 노트 마이그레이션 시작 ===");

        List<Note> allNotes = noteRepository.findAll();
        log.info("총 {} 개의 노트 발견", allNotes.size());

        int successCount = 0;
        int failCount = 0;

        for (Note note : allNotes) {
            try {
                // MongoDB에 이미 있는지 확인
                org.springframework.data.mongodb.core.query.Query query =
                        new org.springframework.data.mongodb.core.query.Query(
                                org.springframework.data.mongodb.core.query.Criteria
                                        .where("note_idx").is(note.getNoteIdx())
                        );

                boolean exists = mongoTemplate.exists(query, "user_notes");

                if (exists) {
                    log.debug("노트 {} 이미 존재, 스킵", note.getNoteIdx());
                    continue;
                }

                // MongoDB에 저장
                Document doc = new Document();
                doc.put("user_idx", note.getUser().getUserIdx());
                doc.put("note_idx", note.getNoteIdx());
                doc.put("title", note.getTitle());
                doc.put("content", note.getContent());
                doc.put("summary", note.getContent()); // 요약본 없으면 원본
                doc.put("embedding", List.of()); // 임베딩은 빈 배열
                doc.put("created_at", note.getCreatedAt());

                mongoTemplate.save(doc, "user_notes");
                successCount++;

                if (successCount % 10 == 0) {
                    log.info("진행률: {}/{}", successCount, allNotes.size());
                }

            } catch (Exception e) {
                failCount++;
                log.error("노트 {} 마이그레이션 실패: {}", note.getNoteIdx(), e.getMessage());
            }
        }

        log.info("=== 마이그레이션 완료 ===");
        log.info("성공: {} 개, 실패: {} 개", successCount, failCount);
    }

    public void migrateLecturesToPostgres() {
    }
}
