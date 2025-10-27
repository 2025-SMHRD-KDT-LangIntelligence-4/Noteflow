package com.smhrd.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class PostgresVectorService {

    private final JdbcTemplate postgresJdbcTemplate;

    @Autowired
    @Qualifier("postgresNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // ✅ PostgreSQL DataSource만 주입
    public PostgresVectorService(@Qualifier("postgresDataSource") DataSource dataSource) {
        this.postgresJdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * ✅ 최근 노트 조회 (PostgreSQL 전용)
     */
    public List<Map<String, Object>> getRecentNotes(Long userIdx, int limit) {
        String sql = """
                SELECT
                    note_idx,
                    user_idx,
                    title,
                    SUBSTRING(content, 1, 100) as content,
                    created_at
                FROM user_notes
                WHERE user_idx = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        try {
            return postgresJdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("note_idx", rs.getLong("note_idx"));
                        row.put("user_idx", rs.getLong("user_idx"));
                        row.put("title", rs.getString("title"));
                        row.put("content", rs.getString("content"));
                        row.put("created_at", rs.getTimestamp("created_at").toLocalDateTime());
                        return row;
                    },
                    userIdx, limit
            );
        } catch (Exception e) {
            log.error("최근 노트 조회 실패 - userIdx: {}", userIdx, e);
            return new ArrayList<>();
        }
    }

    /**
     * 벡터 검색
     */
    public List<Map<String, Object>> searchVectors(
            Long userId,
            List<Float> queryVector,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit) {
        try {
            log.info("🔍 pgvector 검색 시작 - userId: {}, vector_dim: {}", userId, queryVector.size());

            if (userId == null || userId <= 0) {
                log.warn("⚠️ 유효하지 않은 userId: {}", userId);
                return new ArrayList<>();
            }

            String vectorStr = "[" + String.join(",",
                    queryVector.stream().map(Object::toString).toArray(String[]::new)) + "]";

            String sql = """
                    SELECT
                        note_idx,
                        user_idx,
                        title,
                        content,
                        ROUND((1 - (embedding <=> :vector::vector))::numeric, 4) as similarity,
                        created_at
                    FROM user_notes
                    WHERE user_idx = :userId
                      AND embedding IS NOT NULL
                    """ + (startDate != null ? " AND created_at >= :startDate" : "") +
                    (endDate != null ? " AND created_at <= :endDate" : "") + """
                    ORDER BY embedding <=> :vector::vector ASC
                    LIMIT :limit
                    """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("vector", vectorStr)
                    .addValue("limit", limit);

            if (startDate != null) params.addValue("startDate", startDate);
            if (endDate != null) params.addValue("endDate", endDate);

            List<Map<String, Object>> results = namedParameterJdbcTemplate.queryForList(sql, params);
            log.info("✅ pgvector 검색 완료: {} 개 결과 (userId: {})", results.size(), userId);
            return results;

        } catch (Exception e) {
            log.error("❌ pgvector 검색 실패 - userId: {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 사용자 노트 개수
     */
    public long countUserNotes(Long userId) {
        try {
            String sql = "SELECT COUNT(*) FROM user_notes WHERE user_idx = :userId";
            Long count = namedParameterJdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("userId", userId),
                    Long.class
            );
            log.info("📊 사용자 {} 노트 개수: {}", userId, count != null ? count : 0);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ 노트 개수 조회 실패 - userId: {}", userId, e);
            return 0;
        }
    }

    /**
     * 사용자 모든 노트 삭제
     */
    public boolean deleteUserNotes(Long userId) {
        try {
            String sql = "DELETE FROM user_notes WHERE user_idx = :userId";
            int deleted = namedParameterJdbcTemplate.update(
                    sql,
                    new MapSqlParameterSource("userId", userId)
            );
            log.info("🗑️ 사용자 {} 노트 {} 개 삭제", userId, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("❌ 사용자 노트 삭제 실패", e);
            return false;
        }
    }

    /**
     * 단일 노트 삭제
     */
    public boolean deleteNote(Long userId, Long noteIdx) {
        try {
            String sql = "DELETE FROM user_notes WHERE user_idx = :userId AND note_idx = :noteIdx";
            int deleted = namedParameterJdbcTemplate.update(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("noteIdx", noteIdx)
            );
            log.info("🗑️ 노트 삭제: userId={}, noteIdx={}, result={}", userId, noteIdx, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("❌ 노트 삭제 실패", e);
            return false;
        }
    }

    /**
     * 노트 저장 (임베딩 포함)
     */
    public boolean saveUserNote(
            Long userId,
            Long noteIdx,
            String title,
            String content,
            List<Float> embedding) {
        try {
            if (embedding == null || embedding.isEmpty()) {
                log.warn("⚠️ 임베딩이 없습니다");
                return false;
            }

            String vectorStr = "[" + String.join(",",
                    embedding.stream().map(Object::toString).toArray(String[]::new)) + "]";

            String sql = """
                    INSERT INTO user_notes (user_idx, note_idx, title, content, embedding, created_at)
                    VALUES (:userId, :noteIdx, :title, :content, :embedding::vector, NOW())
                    ON CONFLICT (note_idx) DO UPDATE SET
                        embedding = EXCLUDED.embedding,
                        updated_at = NOW()
                    """;

            int saved = namedParameterJdbcTemplate.update(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("noteIdx", noteIdx)
                            .addValue("title", title)
                            .addValue("content", content)
                            .addValue("embedding", vectorStr)
            );

            log.info("✅ 노트 저장: userId={}, noteIdx={}, result={}", userId, noteIdx, saved);
            return saved > 0;

        } catch (Exception e) {
            log.error("❌ 노트 저장 실패", e);
            return false;
        }
    }
}
