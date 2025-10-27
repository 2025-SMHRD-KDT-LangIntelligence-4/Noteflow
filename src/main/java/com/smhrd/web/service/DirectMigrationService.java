package com.smhrd.web.service;

import com.smhrd.web.entity.Note;
import com.smhrd.web.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectMigrationService {

    private final NoteRepository noteRepository;

    @Autowired
    @Qualifier("postgresNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate postgresTemplate;

    @Autowired
    @Qualifier("mysqlNamedParameterJdbcTemplate")  // MySQL 조회용 추가
    private NamedParameterJdbcTemplate mysqlTemplate;

    private final RestTemplate restTemplate;

    /**
     * ✅ 완전 버전: 폴더 경로, 태그 배열 포함
     */
    public void migrateNotesDirectlyToPostgres() {
        log.info("🚀 직접 마이그레이션 시작: MySQL → PostgreSQL pgvector (폴더/태그 포함)");

        List<Note> allNotes = noteRepository.findAll();
        log.info("📊 MySQL에서 {} 개의 노트 발견", allNotes.size());

        int successCount = 0;
        int failCount = 0;

        for (Note note : allNotes) {
            try {
                // 1. 임베딩 생성
                List<Double> embedding = generateEmbedding(note.getContent());
                String vectorString = formatVector(embedding);

                // 2. 폴더 경로 조회
                String folderPath = getFolderPath(note.getFolderId());

                // 3. 태그 배열 생성
                String[] tags = getNoteTags(note);

                // 4. PostgreSQL에 저장 - 폴더/태그 포함
                String sql = """
                    INSERT INTO user_notes 
                    (note_idx, user_idx, title, content, embedding, folder_id, folder_path, tags, created_at)
                    VALUES 
                    (:noteIdx, :userIdx, :title, :content, CAST(:embedding AS vector), 
                     :folderId, :folderPath, :tags, :createdAt)
                    ON CONFLICT (note_idx) DO UPDATE SET
                        content = EXCLUDED.content,
                        embedding = CAST(:embedding AS vector),
                        folder_id = EXCLUDED.folder_id,
                        folder_path = EXCLUDED.folder_path,
                        tags = EXCLUDED.tags
                    """;

                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("noteIdx", note.getNoteIdx())
                        .addValue("userIdx", note.getUser().getUserIdx())
                        .addValue("title", note.getTitle())
                        .addValue("content", note.getContent())
                        .addValue("embedding", vectorString)
                        .addValue("folderId", note.getFolderId())           // ✅ 폴더 ID
                        .addValue("folderPath", folderPath)                 // ✅ 폴더 경로
                        .addValue("tags", tags)                             // ✅ 태그 배열
                        .addValue("createdAt", note.getCreatedAt());

                postgresTemplate.update(sql, params);
                successCount++;

                if (successCount % 10 == 0) {
                    log.info("📈 진행: {} / {} 완료", successCount, allNotes.size());
                }

            } catch (Exception e) {
                failCount++;
                log.error("❌ 노트 {} 실패: {}", note.getNoteIdx(), e.getMessage(), e);
            }
        }

        log.info("✅ 직접 마이그레이션 완료: 성공={}, 실패={}", successCount, failCount);
    }

    /**
     * ✅ 폴더 경로 생성 메서드
     * MySQL에서 폴더 계층 구조를 재귀적으로 조회하여 경로 생성
     */
    private String getFolderPath(Long folderId) {
        if (folderId == null) {
            return "/";
        }

        try {
            // MySQL에서 폴더 정보 조회
            String sql = "SELECT folder_name, parent_folder_id FROM tb_note_folder WHERE folder_idx = :folderId";
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("folderId", folderId);

            Map<String, Object> folder = mysqlTemplate.queryForMap(sql, params);
            String folderName = (String) folder.get("folder_name");
            Long parentFolderId = (Long) folder.get("parent_folder_id");

            // 재귀적으로 부모 경로 생성
            if (parentFolderId == null) {
                return "/" + folderName;
            } else {
                String parentPath = getFolderPath(parentFolderId);
                return parentPath + "/" + folderName;
            }

        } catch (Exception e) {
            log.warn("⚠️ 폴더 경로 조회 실패 (folderId={}): {}", folderId, e.getMessage());
            return "/";
        }
    }

    /**
     * ✅ 노트 태그 배열 생성 메서드
     * Note 엔티티의 noteTags 관계에서 태그 이름 추출
     */
    private String[] getNoteTags(Note note) {
        if (note.getNoteTags() == null || note.getNoteTags().isEmpty()) {
            return new String[0];
        }

        try {
            return note.getNoteTags().stream()
                    .map(noteTag -> noteTag.getTag().getName())
                    .toArray(String[]::new);
        } catch (Exception e) {
            log.warn("⚠️ 태그 추출 실패 (noteIdx={}): {}", note.getNoteIdx(), e.getMessage());
            return new String[0];
        }
    }

    /**
     * 임베딩 생성 (변경 없음)
     */
    private List<Double> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                log.warn("빈 콘텐츠, 기본 벡터 반환");
                return Collections.nCopies(1024, 0.0);
            }

            Map<String, Object> response = restTemplate.postForObject(
                    "http://ssaegim.tplinkdns.com:8081/embed",
                    Map.of("texts", List.of(content.trim())),
                    Map.class
            );

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("임베딩 응답이 null이거나 embeddings 키가 없음");
            }

            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
            return embeddings.get(0);

        } catch (Exception e) {
            log.error("임베딩 생성 실패: {}", e.getMessage());
            return Collections.nCopies(1024, 0.0);
        }
    }

    /**
     * 벡터 포맷팅 (변경 없음)
     */
    private String formatVector(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(d -> String.format("%.15f", d))
                .collect(Collectors.joining(",")) + "]";
    }
}
