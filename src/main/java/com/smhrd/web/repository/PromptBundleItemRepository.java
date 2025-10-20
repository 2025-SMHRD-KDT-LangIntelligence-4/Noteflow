package com.smhrd.web.repository;

import com.smhrd.web.entity.PromptBundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PromptBundleItemRepository extends JpaRepository<PromptBundleItem, Long> {
    List<PromptBundleItem> findByBundleId(Long bundleId);
    List<PromptBundleItem> findByPromptId(Long promptId);
    void deleteByBundleIdAndPromptId(Long bundleId, Long promptId);
}
