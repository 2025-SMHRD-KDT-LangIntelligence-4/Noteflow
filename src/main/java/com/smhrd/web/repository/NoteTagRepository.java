package com.smhrd.web.repository;

import com.smhrd.web.entity.NoteTag;
import com.smhrd.web.entity.Note;
import com.smhrd.web.entity.Tag;

import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteTagRepository extends JpaRepository<NoteTag, Long> {

    /**
     * 특정 노트의 모든 태그 조회
     */
    List<NoteTag> findByNote(Note note);

    /**
     * 특정 태그가 연결된 모든 노트 조회
     */
    List<NoteTag> findByTag(Tag tag);

    /**
     * 노트-태그 연결 존재 여부 확인
     */
    boolean existsByNoteAndTag(Note note, Tag tag);

    /**
     * 특정 노트-태그 연결 삭제
     */
    void deleteByNoteAndTag(Note note, Tag tag);

    /**
     * 특정 노트의 모든 태그 연결 삭제
     */
    void deleteByNote(Note note);

    List<NoteTag> findAllByNote(Note note);
    

    //  노트에 달린 태그명만 빠르게 로딩 (검색용)
     @Query("select lower(nt.tag.name) from NoteTag nt where nt.note.noteIdx = :noteIdx")
     List<String> findTagNamesByNoteIdx(@Param("noteIdx") Long noteIdx);

}