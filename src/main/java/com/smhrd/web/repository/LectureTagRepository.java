package com.smhrd.web.repository;

import com.smhrd.web.entity.LectureTag;
import com.smhrd.web.entity.LectureTagKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LectureTagRepository extends JpaRepository<LectureTag, LectureTagKey> {

    @Query("""
        select lt.lecture.lecIdx as lecIdx,
               lower(lt.tag.name) as tagName
        from LectureTag lt
        where lt.lecture.lecIdx in :ids
        """)
    List<LectureIdTagName> findTagNamesByLectureIds(@Param("ids") List<Long> lectureIds);

    interface LectureIdTagName {
        Long getLecIdx();
        String getTagName();
    }
}