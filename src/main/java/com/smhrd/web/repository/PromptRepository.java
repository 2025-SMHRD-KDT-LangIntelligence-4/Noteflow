package com.smhrd.web.repository;

import com.smhrd.web.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PromptRepository extends JpaRepository<Prompt, Long> {
    Optional<Prompt> findByTitle(String title);
}