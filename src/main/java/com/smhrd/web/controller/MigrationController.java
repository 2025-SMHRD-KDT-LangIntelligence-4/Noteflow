package com.smhrd.web.controller;

import com.smhrd.web.service.DataMigrationService;
import com.smhrd.web.service.DirectMigrationService;
import com.smhrd.web.service.MigrationService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class MigrationController {


    private final MongoTemplate mongoTemplate;
    private final DataMigrationService dataMigrationService;
    private final DirectMigrationService directMigrationService;
    private final MigrationService lectureMigrationService;
    @PostMapping("/migrate-notes")
    public ResponseEntity<Map<String, String>> migrateNotes() {
        try {
            directMigrationService.migrateNotesDirectlyToPostgres();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "마이그레이션 완료"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "실패: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/migrate-notes")  // ← GET 추가
    public ResponseEntity<Map<String, String>> migrateNotesGet() {
        try {
            directMigrationService.migrateNotesDirectlyToPostgres();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "마이그레이션 완료"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "실패: " + e.getMessage()
            ));
        }
    }
    @GetMapping("/mongo-check")
    public ResponseEntity<Map<String, Object>> checkMongo() {
        try {
            long count = mongoTemplate.count(
                    new org.springframework.data.mongodb.core.query.Query(),
                    "user_notes"
            );

            // 샘플 데이터 3개
            List<Document> samples = mongoTemplate.find(
                    new org.springframework.data.mongodb.core.query.Query().limit(3),
                    org.bson.Document.class,
                    "user_notes"
            );

            return ResponseEntity.ok(Map.of(
                    "total_count", count,
                    "samples", samples.stream()
                            .map(doc -> Map.of(
                                    "note_idx", doc.get("note_idx"),
                                    "title", doc.get("title"),
                                    "user_idx", doc.get("user_idx")
                            ))
                            .collect(Collectors.toList())
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
    @PostMapping("/lectures")
    public ResponseEntity<String> migrateLectures() {
        lectureMigrationService.migrateLecturesToPostgres(); // 이거 호출
        return ResponseEntity.ok("완료");
    }
}
