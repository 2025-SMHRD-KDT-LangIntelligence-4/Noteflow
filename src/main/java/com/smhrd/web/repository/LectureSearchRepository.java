package com.smhrd.web.repository;

import com.smhrd.web.entity.Lecture;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LectureSearchRepository extends CrudRepository<Lecture, Long> {

    /**
     * 정확한 태그 매칭 (대소문자 무시)
     * "자바" → "자바"만 매칭
     */
    @Query(value = "SELECT DISTINCT l.* FROM lectures l " +
            "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
            "JOIN tags t ON t.tag_idx = lt.tag_idx " +
            "WHERE LOWER(t.name) = LOWER(:keyword) " +
            "ORDER BY l.lec_idx DESC",
            countQuery = "SELECT COUNT(DISTINCT l.lec_idx) FROM lectures l " +
                    "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
                    "JOIN tags t ON t.tag_idx = lt.tag_idx " +
                    "WHERE LOWER(t.name) = LOWER(:keyword)",
            nativeQuery = true)
    List<Lecture> findByTagNameExact(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 포함 매칭 (부분 일치)
     * "linu" → "linux", "redhat linux"
     */
    @Query(value = "SELECT DISTINCT l.* FROM lectures l " +
            "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
            "JOIN tags t ON t.tag_idx = lt.tag_idx " +
            "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY l.lec_idx DESC",
            countQuery = "SELECT COUNT(DISTINCT l.lec_idx) FROM lectures l " +
                    "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
                    "JOIN tags t ON t.tag_idx = lt.tag_idx " +
                    "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    List<Lecture> findByTagNameContains(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 기존 메서드 (하위 호환성 유지)
     */
    @Query(value = "SELECT DISTINCT l.* FROM lectures l " +
            "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
            "JOIN tags t ON t.tag_idx = lt.tag_idx " +
            "WHERE LOWER(t.name) IN (:names) " +
            "ORDER BY l.lec_idx DESC",
            countQuery = "SELECT COUNT(DISTINCT l.lec_idx) FROM lectures l " +
                    "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
                    "JOIN tags t ON t.tag_idx = lt.tag_idx " +
                    "WHERE LOWER(t.name) IN (:names)",
            nativeQuery = true)
    List<Lecture> findByTagNamesExact(@Param("names") List<String> namesLower, Pageable pageable);

    /**
     * LIKE 검색 (기존 메서드)
     */
    @Query(value = "SELECT DISTINCT l.* FROM lectures l " +
            "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
            "JOIN tags t ON t.tag_idx = lt.tag_idx " +
            "WHERE LOWER(t.name) LIKE CONCAT('%', :kw, '%') " +
            "ORDER BY l.lec_idx DESC",
            countQuery = "SELECT COUNT(DISTINCT l.lec_idx) FROM lectures l " +
                    "JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx " +
                    "JOIN tags t ON t.tag_idx = lt.tag_idx " +
                    "WHERE LOWER(t.name) LIKE CONCAT('%', :kw, '%')",
            nativeQuery = true)
    List<Lecture> findByTagNameLikePage(@Param("kw") String kwLower, Pageable pageable);
}
