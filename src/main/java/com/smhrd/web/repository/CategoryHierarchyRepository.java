package com.smhrd.web.repository;

import com.smhrd.web.entity.CategoryHierarchy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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



    @Query("SELECT c FROM CategoryHierarchy c WHERE LOWER(c.keywords) LIKE %:keyword%")
    List<CategoryHierarchy> findByKeyword(@Param("keyword") String keyword);

    default List<CategoryHierarchy> findCandidatesByKeywords(Set<String> keywords) {
        List<CategoryHierarchy> results = new ArrayList<>();
        for (String keyword : keywords) {
            results.addAll(findByKeyword(keyword.toLowerCase()));
        }
        return results.stream().distinct().collect(Collectors.toList());
    }

}
