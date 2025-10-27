package com.smhrd.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LectureRecommendService {

    @Qualifier("mysqlNamedParameterJdbcTemplate")
    private final NamedParameterJdbcTemplate mysqlTemplate;

    /**
     * 사용자의 취약 주제 기반 강의 추천
     */
    public List<Map<String, Object>> recommendLecturesByWeakness(Long userId) {
        try {
            log.info("🔍 사용자 {} 취약점 기반 강의 추천 시작", userId);

            // 1️⃣ 사용자의 오답 태그 조회
            String weakTagsSql = """
                SELECT tg.tag_name, COUNT(*) as mistake_count
                FROM exam_answer ea
                JOIN exam_problem ep ON ea.exam_problem_idx = ep.exam_problem_idx
                JOIN problem_tag pt ON ep.exam_problem_idx = pt.exam_problem_idx
                JOIN tag tg ON pt.tag_idx = tg.tag_idx
                WHERE ea.user_idx = :userId AND ea.is_correct = 0
                GROUP BY tg.tag_name
                ORDER BY mistake_count DESC
                LIMIT 5
            """;

            List<Map<String, Object>> weakTags = mysqlTemplate.queryForList(
                    weakTagsSql,
                    Map.of("userId", userId)
            );

            if (weakTags.isEmpty()) {
                log.warn("⚠️ 사용자 {}의 오답이 없습니다.", userId);
                return new ArrayList<>();
            }

            log.info("📊 취약 태그: {} 개", weakTags.size());

            // 2️⃣ 태그와 연결된 강의 조회 - ✅ lecture → lectures, lec_desc 제거
            String lecturesSql = """
                SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                FROM lectures l
                JOIN lecture_tag lt ON l.lec_idx = lt.lec_idx
                JOIN tag tg ON lt.tag_idx = tg.tag_idx
                WHERE tg.tag_name IN (:tagNames)
                LIMIT 5
            """;

            List<String> tagNames = weakTags.stream()
                    .map(m -> (String) m.get("tag_name"))
                    .toList();

            List<Map<String, Object>> recommendedLectures = mysqlTemplate.queryForList(
                    lecturesSql,
                    Map.of("tagNames", tagNames)
            );

            log.info("✅ 추천 강의: {} 개", recommendedLectures.size());
            return recommendedLectures;

        } catch (Exception e) {
            log.error("❌ 강의 추천 중 오류", e);
            return new ArrayList<>();
        }
    }

    /**
     * 인기 있는 강의 TOP 10 조회 - ✅ lecture → lectures, lec_desc 제거
     */
    public List<Map<String, Object>> getPopularLectures() {
        try {
            log.info("🔍 인기 강의 TOP 10 조회");
            String sql = """
                SELECT l.lec_idx, l.lec_title, l.lec_url,
                       COUNT(DISTINCT le.user_idx) as enrollment_count
                FROM lectures l
                LEFT JOIN lecture_enrollment le ON l.lec_idx = le.lec_idx
                GROUP BY l.lec_idx
                ORDER BY enrollment_count DESC
                LIMIT 10
            """;

            List<Map<String, Object>> result = mysqlTemplate.queryForList(sql, new HashMap<>());
            log.info("✅ 인기 강의 조회 완료: {} 개", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ 인기 강의 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 특정 태그의 강의 조회 - ✅ lecture → lectures, lec_desc 제거
     */
    public List<Map<String, Object>> getLecturesByTag(String tagName) {
        try {
            log.info("🔍 태그 '{}' 강의 조회", tagName);
            String sql = """
                SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                FROM lectures l
                JOIN lecture_tag lt ON l.lec_idx = lt.lec_idx
                JOIN tag tg ON lt.tag_idx = tg.tag_idx
                WHERE tg.tag_name = :tagName
            """;

            List<Map<String, Object>> result = mysqlTemplate.queryForList(
                    sql,
                    Map.of("tagName", tagName)
            );

            log.info("✅ 태그별 강의 조회 완료: {} 개", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ 태그별 강의 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 사용자의 학습 진도 기반 다음 강의 추천 - ✅ lecture → lectures, lec_desc 제거
     */
    public List<Map<String, Object>> getNextRecommendedLectures(Long userId) {
        try {
            log.info("🔍 사용자 {} 다음 강의 추천", userId);
            String sql = """
                SELECT l.lec_idx, l.lec_title, l.lec_url,
                       CASE
                           WHEN le.user_idx IS NOT NULL THEN '수강중'
                           ELSE '추천'
                       END as status
                FROM lectures l
                LEFT JOIN lecture_enrollment le ON l.lec_idx = le.lec_idx AND le.user_idx = :userId
                WHERE l.lec_idx NOT IN (
                    SELECT lec_idx FROM lecture_enrollment WHERE user_idx = :userId
                )
                ORDER BY RAND()
                LIMIT 3
            """;

            List<Map<String, Object>> result = mysqlTemplate.queryForList(
                    sql,
                    Map.of("userId", userId)
            );

            log.info("✅ 다음 강의 추천 완료: {} 개", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ 다음 강의 추천 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 오답률 기반 상세 강의 추천 - ✅ lecture → lectures, lec_desc 제거
     */
    public Map<String, Object> getDetailedWeaknessAnalysis(Long userId) {
        try {
            log.info("📊 사용자 {} 상세 취약점 분석 시작", userId);
            Map<String, Object> result = new HashMap<>();

            // 1️⃣ 취약 태그 및 오답률 계산
            String weaknessSql = """
                SELECT tg.tag_name,
                       COUNT(*) as total_questions,
                       SUM(CASE WHEN ea.is_correct = 0 THEN 1 ELSE 0 END) as wrong_count,
                       ROUND(SUM(CASE WHEN ea.is_correct = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) as error_rate
                FROM exam_answer ea
                JOIN exam_problem ep ON ea.exam_problem_idx = ep.exam_problem_idx
                JOIN problem_tag pt ON ep.exam_problem_idx = pt.exam_problem_idx
                JOIN tag tg ON pt.tag_idx = tg.tag_idx
                WHERE ea.user_idx = :userId
                GROUP BY tg.tag_name
                ORDER BY error_rate DESC
                LIMIT 5
            """;

            List<Map<String, Object>> weaknessDetails = mysqlTemplate.queryForList(
                    weaknessSql,
                    Map.of("userId", userId)
            );

            result.put("weakness_analysis", weaknessDetails);
            log.info("📊 취약점 분석: {} 개 태그", weaknessDetails.size());

            // 2️⃣ 취약점에 맞는 강의 추천
            List<String> weakTags = weaknessDetails.stream()
                    .map(m -> (String) m.get("tag_name"))
                    .toList();

            if (!weakTags.isEmpty()) {
                String lectureSql = """
                    SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                    FROM lectures l
                    JOIN lecture_tag lt ON l.lec_idx = lt.lec_idx
                    JOIN tag tg ON lt.tag_idx = tg.tag_idx
                    WHERE tg.tag_name IN (:tagNames)
                    LIMIT 3
                """;

                List<Map<String, Object>> recommendedLectures = mysqlTemplate.queryForList(
                        lectureSql,
                        Map.of("tagNames", weakTags)
                );

                result.put("recommended_lectures", recommendedLectures);
                log.info("🎓 추천 강의: {} 개", recommendedLectures.size());
            }

            // 3️⃣ 오답 모음
            String wrongProblemsSql = """
                SELECT ep.exam_problem_idx, ep.problem_content, tg.tag_name
                FROM exam_answer ea
                JOIN exam_problem ep ON ea.exam_problem_idx = ep.exam_problem_idx
                JOIN problem_tag pt ON ep.exam_problem_idx = pt.exam_problem_idx
                JOIN tag tg ON pt.tag_idx = tg.tag_idx
                WHERE ea.user_idx = :userId AND ea.is_correct = 0
                ORDER BY ea.created_at DESC
                LIMIT 5
            """;

            List<Map<String, Object>> wrongProblems = mysqlTemplate.queryForList(
                    wrongProblemsSql,
                    Map.of("userId", userId)
            );

            result.put("wrong_problems", wrongProblems);
            log.info("❌ 오답 문제: {} 개", wrongProblems.size());
            log.info("✅ 상세 분석 완료");
            return result;

        } catch (Exception e) {
            log.error("❌ 상세 분석 실패", e);
            return new HashMap<>();
        }
    }

    /**
     * 키워드로 강의 검색 - ✅ lec_desc 제거, FULLTEXT 인덱스 활용
     */
    public List<Map<String, Object>> searchByKeyword(String keyword, String category, int limit) {
        try {
            log.info("🔍 키워드 검색: {}", keyword);
            String sql = """
            SELECT lec_idx, lec_title, lec_url, category_small
            FROM lectures
            WHERE MATCH(lec_title) AGAINST(:keyword IN BOOLEAN MODE)
            LIMIT :limit
        """;

            List<Map<String, Object>> result = mysqlTemplate.queryForList(
                    sql,
                    Map.of("keyword", keyword, "limit", limit)
            );

            log.info("✅ 키워드 검색 완료: {} 개", result.size());
            return result;

        } catch (Exception e) {
            log.error("❌ 키워드 검색 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 태그로 강의 검색 (AND/OR 모드)
     */
    public List<Map<String, Object>> searchByTags(List<String> tags, String mode, String category, int limit) {
        try {
            log.info("🔍 태그 검색: {} (모드: {})", tags, mode);

            if ("AND".equals(mode)) {
                // AND 로직
                StringBuilder sql = new StringBuilder("""
                    SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                    FROM lectures l
                """);

                for (int i = 0; i < tags.size(); i++) {
                    sql.append(" JOIN lecture_tags lt").append(i).append(" ON l.lec_idx = lt").append(i).append(".lec_idx")
                            .append(" JOIN tags t").append(i).append(" ON lt").append(i).append(".tag_idx = t").append(i).append(".tag_idx");
                }

                sql.append(" WHERE ");
                for (int i = 0; i < tags.size(); i++) {
                    if (i > 0) sql.append(" AND ");
                    sql.append("t").append(i).append(".name = :tag").append(i);
                }

                sql.append(" LIMIT :limit");

                Map<String, Object> params = new HashMap<>();
                for (int i = 0; i < tags.size(); i++) {
                    params.put("tag" + i, tags.get(i));
                }
                params.put("limit", limit);

                return mysqlTemplate.queryForList(sql.toString(), params);

            } else {
                // OR 로직
                StringBuilder sql = new StringBuilder("""
                    SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                    FROM lectures l
                    JOIN lecture_tags lt ON l.lec_idx = lt.lec_idx
                    JOIN tags t ON lt.tag_idx = t.tag_idx
                    WHERE t.name IN (
                """);

                for (int i = 0; i < tags.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(":tag").append(i);
                }

                sql.append(")\nLIMIT :limit");

                Map<String, Object> params = new HashMap<>();
                for (int i = 0; i < tags.size(); i++) {
                    params.put("tag" + i, tags.get(i));
                }
                params.put("limit", limit);

                return mysqlTemplate.queryForList(sql.toString(), params);
            }

        } catch (Exception e) {
            log.error("❌ 태그 검색 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 키워드 + 태그 하이브리드 검색 - ✅ FULLTEXT 사용
     */
    public List<Map<String, Object>> searchByKeywordAndTags(String keyword, List<String> tags, String mode, String category, int limit) {
        try {
            log.info("🔍 하이브리드 검색: keyword={}, tags={}, mode={}", keyword, tags, mode);

            String sql = """
                SELECT DISTINCT l.lec_idx, l.lec_title, l.lec_url
                FROM lectures l
                LEFT JOIN lecture_tags lt ON l.lec_idx = lt.lec_idx
                LEFT JOIN tags t ON lt.tag_idx = t.tag_idx
                WHERE MATCH(l.lec_title) AGAINST(:keyword IN BOOLEAN MODE)
            """ + ("AND".equals(mode)
                    ? " AND t.name IN (:tags)"
                    : " OR t.name IN (:tags)") + """
                GROUP BY l.lec_idx
                LIMIT :limit
            """;

            return mysqlTemplate.queryForList(
                    sql,
                    Map.of(
                            "keyword", keyword,
                            "tags", tags,
                            "limit", limit
                    )
            );

        } catch (Exception e) {
            log.error("❌ 하이브리드 검색 실패", e);
            return new ArrayList<>();
        }
    }
}
