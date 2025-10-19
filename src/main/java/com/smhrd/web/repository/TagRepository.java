package com.smhrd.web.repository;

import com.smhrd.web.entity.Tag;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Modifying
    @Query(value = "UPDATE tags SET usage_count = GREATEST(0, usage_count + :delta) WHERE tag_idx = :tagIdx", nativeQuery = true)
    int bumpUsage(@Param("tagIdx") Long tagIdx, @Param("delta") int delta);

    // ✅ 자동완성용 (이름으로 검색, 사용빈도 높은 순)
    @Query("SELECT t.name FROM Tag t WHERE t.name LIKE CONCAT(:prefix, '%') ORDER BY t.usageCount DESC")
    List<String> findTop10ByNameStartingWithOrderByUsageCountDesc(@Param("prefix") String prefix);

    // ✅ 인기 태그 조회
    List<Tag> findTop20ByOrderByUsageCountDesc();

}