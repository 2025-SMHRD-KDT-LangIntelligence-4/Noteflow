package com.smhrd.web.repository;

import com.smhrd.web.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
    Optional<Prompt> findByTitle(String title);
    List<Prompt> findAllByOrderByPriorityDescCreatedAtDesc();
    List<Prompt> findByTitleContainingOrderByPriorityDesc(String keyword);
    

    @Query("SELECT p FROM Prompt p WHERE p.promptId = :promptId")
    Optional<Prompt> findByPromptIdValue(@Param("promptId") Long promptId);
	
    List<Prompt> findAllByTitle(String title);

}