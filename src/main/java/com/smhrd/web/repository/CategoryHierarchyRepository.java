package com.smhrd.web.repository;

import com.smhrd.web.entity.CategoryHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryHierarchyRepository extends JpaRepository<CategoryHierarchy, Long> {

    List<CategoryHierarchy> findByLargeCategory(String largeCategory);

    List<CategoryHierarchy> findByLargeCategoryAndMediumCategory(String largeCategory, String mediumCategory);

    @Query("SELECT c FROM CategoryHierarchy c WHERE c.keywords LIKE %:keyword%")
    List<CategoryHierarchy> findByKeywordsContaining(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT c.largeCategory FROM CategoryHierarchy c ORDER BY c.largeCategory")
    List<String> findDistinctLargeCategories();

    @Query("SELECT DISTINCT c.mediumCategory FROM CategoryHierarchy c WHERE c.largeCategory = :largeCategory ORDER BY c.mediumCategory")
    List<String> findDistinctMediumCategories(@Param("largeCategory") String largeCategory);
}
