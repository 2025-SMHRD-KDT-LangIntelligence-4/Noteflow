package com.smhrd.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB 노트 데이터 → PostgreSQL pgvector로 마이그레이션
 *
 * 목표:
 * 1. MongoDB의 notes 콜렉션에서 모든 노트 조회
 * 2. 각 노트의 content에 대해 Embedding 생성
 * 3. PostgreSQL user_notes 테이블에 저장
 * 4. 벡터 유사도 검색 가능하게 구성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final MongoTemplate mongoTemplate;

    @Autowired
    @Qualifier("postgresNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate postgresTemplate;

    private final RestTemplate restTemplate;

    /**
     * MongoDB 모든 노트 → PostgreSQL pgvector로 마이그레이션
     * 실행: 서버 시작 후 한 번만 실행하거나, Admin API로 호출
     */
    public void migrateAllNotesFromMongoToPostgres() {
        try {
            log.info("🔄 데이터 마이그레이션 시작: MongoDB → PostgreSQL pgvector");

            // 1️⃣ MongoDB에서 모든 노트 조회
            Query query = new Query();
            List<Document> mongoNotes = mongoTemplate.find(query, Document.class, "notes");

            if (mongoNotes == null || mongoNotes.isEmpty()) {
                log.warn("⚠️ MongoDB에 노트가 없습니다.");
                return;
            }

            log.info("📊 마이그레이션할 노트 개수: {}", mongoNotes.size());

            int successCount = 0;
            int failCount = 0;

            // 2️⃣ 각 노트에 대해 처리
            for (Document doc : mongoNotes) {
                try {
                    Long noteIdx = doc.getLong("note_idx") != null ? doc.getLong("note_idx") : System.currentTimeMillis();
                    Long userIdx = doc.getLong("user_idx");
                    String title = doc.getString("title");
                    String content = doc.getString("content");
                    List<String> keywords = (List<String>) doc.get("keywords");
                    LocalDateTime createdAt = doc.getDate("created_at") != null
                            ? doc.getDate("created_at").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                            : LocalDateTime.now();
                    String fileId = doc.getString("file_id");

                    // 3️⃣ Content를 벡터화 (Embedding 생성)
                    List<Float> embedding = generateEmbeddingForNote(content);

                    if (embedding == null || embedding.isEmpty()) {
                        log.warn("⚠️ Embedding 생성 실패: noteIdx={}", noteIdx);
                        failCount++;
                        continue;
                    }

                    // 4️⃣ PostgreSQL에 저장
                    saveNoteToPgvector(noteIdx, userIdx, title, content, keywords, embedding, fileId, createdAt);
                    successCount++;

                    if (successCount % 50 == 0) {
                        log.info("📈 진행: {} 개 완료", successCount);
                    }

                } catch (Exception e) {
                    log.error("❌ 노트 마이그레이션 실패", e);
                    failCount++;
                }
            }

            log.info("✅ 마이그레이션 완료: 성공={}, 실패={}", successCount, failCount);

        } catch (Exception e) {
            log.error("❌ 마이그레이션 중 오류 발생", e);
        }
    }

    /**
     * 특정 사용자의 노트만 마이그레이션
     */
    public void migrateUserNotes(Long userIdx) {
        try {
            log.info("🔄 사용자 {} 노트 마이그레이션 시작", userIdx);

            // MongoDB에서 사용자의 노트만 조회
            Query query = new Query(Criteria.where("user_idx").is(userIdx));
            List<Document> userNotes = mongoTemplate.find(query, Document.class, "notes");

            if (userNotes == null || userNotes.isEmpty()) {
                log.warn("⚠️ 사용자 {}의 노트가 없습니다.", userIdx);
                return;
            }

            log.info("📊 사용자 {} 마이그레이션할 노트 개수: {}", userIdx, userNotes.size());

            int successCount = 0;
            for (Document doc : userNotes) {
                try {
                    Long noteIdx = doc.getLong("note_idx") != null ? doc.getLong("note_idx") : System.currentTimeMillis();
                    String title = doc.getString("title");
                    String content = doc.getString("content");
                    List<String> keywords = (List<String>) doc.get("keywords");
                    LocalDateTime createdAt = doc.getDate("created_at") != null
                            ? doc.getDate("created_at").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                            : LocalDateTime.now();
                    String fileId = doc.getString("file_id");

                    // Embedding 생성
                    List<Float> embedding = generateEmbeddingForNote(content);

                    if (embedding != null && !embedding.isEmpty()) {
                        saveNoteToPgvector(noteIdx, userIdx, title, content, keywords, embedding, fileId, createdAt);
                        successCount++;
                    }

                } catch (Exception e) {
                    log.error("❌ 노트 처리 실패", e);
                }
            }

            log.info("✅ 사용자 {} 마이그레이션 완료: {} 개", userIdx, successCount);

        } catch (Exception e) {
            log.error("❌ 사용자 마이그레이션 중 오류", e);
        }
    }

    /**
     * 노트의 content에 대해 Embedding 생성
     */
    private List<Float> generateEmbeddingForNote(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Embedding 서버 호출
            Map<String, Object> response = restTemplate.postForObject(
                    "http://ssaegim.tplinkdns.com:8081/embed",
                    Map.of("texts", List.of(content)),
                    Map.class
            );

            if (response == null) {
                log.error("❌ Embedding 응답 null");
                return new ArrayList<>();
            }

            // 응답 파싱
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            if (embeddings == null || embeddings.isEmpty()) {
                log.error("❌ embeddings 배열 없음");
                return new ArrayList<>();
            }

            // Float으로 변환
            List<Float> result = embeddings.get(0).stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());

            return result;

        } catch (Exception e) {
            log.error("❌ Embedding 생성 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * PostgreSQL pgvector에 노트 저장
     */
    private void saveNoteToPgvector(
            Long noteIdx,
            Long userIdx,
            String title,
            String content,
            List<String> keywords,
            List<Float> embedding,
            String fileId,
            LocalDateTime createdAt) {

        try {
            String sql = """
                INSERT INTO user_notes (note_idx, user_idx, title, content, keywords, embedding, file_id, created_at)
                VALUES (:noteIdx, :userIdx, :title, :content, :keywords, :embedding::vector, :fileId, :createdAt)
                ON CONFLICT (note_idx) DO UPDATE SET
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    keywords = EXCLUDED.keywords,
                    embedding = EXCLUDED.embedding,
                    file_id = EXCLUDED.file_id
                """;

            Map<String, Object> params = new HashMap<>();
            params.put("noteIdx", noteIdx);
            params.put("userIdx", userIdx);
            params.put("title", title);
            params.put("content", content);
            params.put("keywords", keywords != null ? keywords.toArray(new String[0]) : new String[0]);
            params.put("embedding", "[" + String.join(",", embedding.stream().map(String::valueOf).toList()) + "]");
            params.put("fileId", fileId);
            params.put("createdAt", createdAt);

            postgresTemplate.update(sql, params);

        } catch (Exception e) {
            log.error("❌ PostgreSQL 저장 실패: noteIdx={}", noteIdx, e);
            throw new RuntimeException("PostgreSQL 저장 실패", e);
        }
    }

    /**
     * 마이그레이션 상태 확인
     */
    public Map<String, Object> getMigrationStatus() {
        try {
            String sql = "SELECT COUNT(*) as total_notes FROM user_notes";
            Map<String, Object> result = postgresTemplate.queryForMap(sql, new HashMap<>());

            return Map.of(
                    "total_notes_in_pgvector", result.get("total_notes"),
                    "status", "마이그레이션 완료 또는 진행 중"
            );
        } catch (Exception e) {
            return Map.of("error", "상태 조회 실패", "message", e.getMessage());
        }
    }
}