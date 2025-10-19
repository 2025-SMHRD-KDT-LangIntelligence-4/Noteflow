package com.smhrd.web.repository;

import com.smhrd.web.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {
    List<Lecture> findByCategoryLargeOrderByCreatedAtDesc(String categoryLarge);
    List<Lecture> findByCategoryLargeAndCategoryMediumOrderByCreatedAtDesc(String categoryLarge, String categoryMedium);
    List<Lecture> findByCategoryLargeAndCategoryMediumAndCategorySmallOrderByCreatedAtDesc(String categoryLarge, String categoryMedium, String categorySmall);
    List<Lecture> findByLecTitleContainingOrderByCreatedAtDesc(String keyword);
}
