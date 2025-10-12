package com.smhrd.web.repository;

import com.smhrd.web.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 태그명으로 검색
     */
    Optional<Tag> findByName(String name);

    /**
     * 태그명 존재 여부 확인
     */
    boolean existsByName(String name);
}