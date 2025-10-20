package com.smhrd.web.repository;

import com.smhrd.web.entity.LectureTag;
import com.smhrd.web.entity.LectureTagKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LectureTagRepository extends JpaRepository<LectureTag, LectureTagKey> {

    /**
     * 강의 ID 목록으로 태그 이름 조회 (Projection)
     */
    @Query("SELECT lt.lecture.lecIdx as lecIdx, lt.tag.name as tagName " +
            "FROM LectureTag lt " +
            "WHERE lt.lecture.lecIdx IN :ids")
    List<LectureIdTagName> findTagNamesByLectureIds(@Param("ids") List<Long> lectureIds);

    /**
     * 강의 ID 목록으로 태그 정보 조회 (Object[] 반환)
     */
    @Query("SELECT lt.lecture.lecIdx, lt.tag.name " +
            "FROM LectureTag lt " +
            "WHERE lt.lecture.lecIdx IN :ids")
    List<Object[]> findTagsByLectureIds(@Param("ids") List<Long> lecIds);

    /**
     * Projection 인터페이스
     */
    interface LectureIdTagName {
        Long getLecIdx();
        String getTagName();
    }
}
