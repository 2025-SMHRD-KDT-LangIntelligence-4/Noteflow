package com.smhrd.web.dto;

import com.smhrd.web.entity.CategoryHierarchy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 카테고리 분류 결과 DTO
 * - 키워드 추출 및 카테고리 매칭 결과
 * - 대/중/소 분류 정보
 * - 신뢰도 및 폴더 경로
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResult {

    // ========================================
    // 키워드 추출 결과
    // ========================================

    /**
     * 추출된 키워드 목록
     */
    private Set<String> extractedKeywords;

    // ========================================
    // 카테고리 매칭 결과
    // ========================================

    /**
     * 매칭된 카테고리 엔티티
     */
    private CategoryHierarchy matchedCategory;

    /**
     * 대분류 (수동 설정 가능)
     */
    private String largeCategory;

    /**
     * 중분류 (수동 설정 가능)
     */
    private String mediumCategory;

    /**
     * 소분류 (수동 설정 가능)
     */
    private String smallCategory;

    // ========================================
    // 신뢰도 및 경로
    // ========================================

    /**
     * 매칭 신뢰도 (0.0 ~ 1.0)
     */
    private double confidence;

    /**
     * 폴더 경로 (예: "국어/문법/품사")
     */
    private String suggestedFolderPath;

    // ========================================
    // 편의 메소드
    // ========================================

    /**
     * 포맷된 태그 반환
     * @return 예: "[국어 > 문법 > 품사]" 또는 "[미분류]"
     */
    public String getFormattedTag() {
        if (matchedCategory != null) {
            return matchedCategory.getExampleTag();
        }

        // matchedCategory가 없으면 수동 설정된 값으로 생성
        if (largeCategory != null) {
            StringBuilder tag = new StringBuilder("[");
            tag.append(largeCategory);

            if (mediumCategory != null) {
                tag.append(" > ").append(mediumCategory);

                if (smallCategory != null) {
                    tag.append(" > ").append(smallCategory);
                }
            }

            tag.append("]");
            return tag.toString();
        }

        return "[미분류]";
    }

    /**
     * 고신뢰도 여부 판단
     * @return 신뢰도 70% 이상이면 true
     */
    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }

    /**
     * 카테고리가 설정되었는지 확인
     * @return 대분류 이상이 설정되어 있으면 true
     */
    public boolean hasCategory() {
        return largeCategory != null || matchedCategory != null;
    }

    /**
     * 폴더 경로 생성
     * @return "대분류/중분류/소분류" 형태 (null이면 빈 문자열)
     */
    public String generateFolderPath() {
        if (suggestedFolderPath != null) {
            return suggestedFolderPath;
        }

        StringBuilder path = new StringBuilder();

        String large = largeCategory != null ? largeCategory :
                (matchedCategory != null ? matchedCategory.getLargeCategory() : null);
        String medium = mediumCategory != null ? mediumCategory :
                (matchedCategory != null ? matchedCategory.getMediumCategory() : null);
        String small = smallCategory != null ? smallCategory :
                (matchedCategory != null ? matchedCategory.getSmallCategory() : null);

        if (large != null) {
            path.append(large);

            if (medium != null) {
                path.append("/").append(medium);

                if (small != null) {
                    path.append("/").append(small);
                }
            }
        }

        return path.toString();
    }

    // ========================================
    // Getter 보완 (matchedCategory 우선)
    // ========================================

    /**
     * 대분류 조회 (matchedCategory 우선)
     */
    public String getLargeCategory() {
        if (largeCategory != null) {
            return largeCategory;
        }
        return matchedCategory != null ? matchedCategory.getLargeCategory() : null;
    }

    /**
     * 중분류 조회 (matchedCategory 우선)
     */
    public String getMediumCategory() {
        if (mediumCategory != null) {
            return mediumCategory;
        }
        return matchedCategory != null ? matchedCategory.getMediumCategory() : null;
    }

    /**
     * 소분류 조회 (matchedCategory 우선)
     */
    public String getSmallCategory() {
        if (smallCategory != null) {
            return smallCategory;
        }
        return matchedCategory != null ? matchedCategory.getSmallCategory() : null;
    }
}
