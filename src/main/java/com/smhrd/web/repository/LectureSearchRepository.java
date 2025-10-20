// src/main/java/com/smhrd/web/repository/LectureSearchRepository.java
package com.smhrd.web.repository;

import com.smhrd.web.entity.Lecture;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LectureSearchRepository extends CrudRepository<Lecture, Long> {

    // A) 태그 "정확" 일치 — DISTINCT로 그룹 제거 (ONLY_FULL_GROUP_BY 회피)
    @Query(value = """
        SELECT DISTINCT l.*
        FROM lectures l
        JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx
        JOIN tags t ON t.tag_idx = lt.tag_idx
        WHERE LOWER(t.name) IN (:names)
        ORDER BY l.lec_idx DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT l.lec_idx)
        FROM lectures l
        JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx
        JOIN tags t ON t.tag_idx = lt.tag_idx
        WHERE LOWER(t.name) IN (:names)
        """,
        nativeQuery = true)
    List<Lecture> findByTagNamesExact(@Param("names") List<String> namesLower, Pageable pageable);

    // B) 태그 "부분" 일치 — LIKE + DISTINCT
    @Query(value = """
        SELECT DISTINCT l.*
        FROM lectures l
        JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx
        JOIN tags t ON t.tag_idx = lt.tag_idx
        WHERE LOWER(t.name) LIKE CONCAT('%', :kw, '%')
        ORDER BY l.lec_idx DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT l.lec_idx)
        FROM lectures l
        JOIN lecture_tags lt ON lt.lec_idx = l.lec_idx
        JOIN tags t ON t.tag_idx = lt.tag_idx
        WHERE LOWER(t.name) LIKE CONCAT('%', :kw, '%')
        """,
        nativeQuery = true)
    List<Lecture> findByTagNameLikePage(@Param("kw") String kwLower, Pageable pageable);
}
