package com.smhrd.web.dto;

import com.smhrd.web.entity.CategoryHierarchy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResult {
    private Set<String> extractedKeywords;
    private CategoryHierarchy matchedCategory;
    private double confidence;
    private String suggestedFolderPath;

    public String getFormattedTag() {
        return matchedCategory != null ? matchedCategory.getExampleTag() : "[미분류]";
    }

    public boolean isHighConfidence() {
        return confidence >= 0.7; // 70% 이상이면 고신뢰도
    }
}
