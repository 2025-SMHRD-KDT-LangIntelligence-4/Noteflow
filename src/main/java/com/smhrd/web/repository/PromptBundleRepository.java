package com.smhrd.web.repository;

import com.smhrd.web.entity.PromptBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromptBundleRepository extends JpaRepository<PromptBundle, Long> {
}
