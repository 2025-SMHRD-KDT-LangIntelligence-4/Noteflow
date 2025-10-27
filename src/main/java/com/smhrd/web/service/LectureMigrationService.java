package com.smhrd.web.service;

import com.smhrd.web.entity.Lecture;
import com.smhrd.web.repository.LectureRepository;
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
public class LectureMigrationService {

    private final LectureRepository lectureRepository;

    @Autowired
    @Qualifier("postgresNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate postgresTemplate;

    @Autowired
    @Qualifier("mysqlNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate mysqlTemplate;

    private final RestTemplate restTemplate;

    /**
     * ✅ 강의 마이그레이션: MySQL → PostgreSQL pgvector
     * 제목 + 카테고리 기반 임베딩 생성
     */
    public void migrateLecturesToPostgres() {
        log.info("🚀 강의 마이그레이션 시작: MySQL → PostgreSQL pgvector");

        List<Lecture> allLectures = lectureRepository.findAll();
        log.info("📊 MySQL에서 {} 개의 강의 발견", allLectures.size());

        int successCount = 0;
        int failCount = 0;

        for (Lecture lecture : allLectures) {
            try {
                // 1. 임베딩 텍스트 구성: "제목 | 대분류 > 중분류 > 소분류"
                String embeddingText = String.format("%s | %s > %s > %s",
                        lecture.getLecTitle(),
                        lecture.getCategoryLarge(),
                        lecture.getCategoryMedium(),
                        lecture.getCategorySmall()
                );

                // 2. 임베딩 생성
                List<Double> embedding = generateEmbedding(embeddingText);
                String vectorString = formatVector(embedding);

                // 3. 태그 배열 조회
                String[] tags = getLectureTags(lecture.getLecIdx());

                // 4. PostgreSQL에 저장
                String sql = """
                    INSERT INTO course_embeddings
                    (lec_idx, title, url, category_large, category_medium, category_small,
                     tags, embedding, created_at)
                    VALUES
                    (:lecIdx, :title, :url, :categoryLarge, :categoryMedium, :categorySmall,
                     :tags, CAST(:embedding AS vector), :createdAt)
                    ON CONFLICT (lec_idx) DO UPDATE SET
                        title = EXCLUDED.title,
                        embedding = CAST(:embedding AS vector),
                        tags = EXCLUDED.tags
                    """;

                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("lecIdx", lecture.getLecIdx())
                        .addValue("title", lecture.getLecTitle())
                        .addValue("url", lecture.getLecUrl())
                        .addValue("categoryLarge", lecture.getCategoryLarge())
                        .addValue("categoryMedium", lecture.getCategoryMedium())
                        .addValue("categorySmall", lecture.getCategorySmall())
                        .addValue("tags", tags)
                        .addValue("embedding", vectorString)
                        .addValue("createdAt", lecture.getCreatedAt());

                postgresTemplate.update(sql, params);
                successCount++;

                if (successCount % 100 == 0) {
                    log.info("📈 진행: {} / {} 완료", successCount, allLectures.size());
                }

            } catch (Exception e) {
                failCount++;
                log.error("❌ 강의 {} 실패: {}", lecture.getLecIdx(), e.getMessage(), e);
            }
        }

        log.info("✅ 강의 마이그레이션 완료: 성공={}, 실패={}", successCount, failCount);
    }

    /**
     * ✅ 강의 태그 배열 조회
     */
    private String[] getLectureTags(Long lecIdx) {
        try {
            String sql = """
                SELECT t.name
                FROM lecture_tags lt
                JOIN tags t ON lt.tag_idx = t.tag_idx
                WHERE lt.lec_idx = :lecIdx
                """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("lecIdx", lecIdx);

            List<String> tagList = mysqlTemplate.query(sql, params,
                    (rs, rowNum) -> rs.getString("name"));

            return tagList.toArray(new String[0]);

        } catch (Exception e) {
            log.warn("⚠️ 태그 조회 실패 (lecIdx={}): {}", lecIdx, e.getMessage());
            return new String[0];
        }
    }

    /**
     * ✅ 임베딩 생성
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
     * ✅ 벡터 포맷팅
     */
    private String formatVector(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(d -> String.format("%.15f", d))
                .collect(Collectors.joining(",")) + "]";
    }
}
