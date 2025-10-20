package com.smhrd.web.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureDto {
	private Long lecIdx;
	private String lecTitle;
	private String lecUrl;
	private String categoryLarge;
	private String categoryMedium;
	private String categorySmall;

	// 강의에 달린 전체 태그(소문자)
	private List<String> lectureTags;

	// 노트 태그와의 교집합(소문자)
	private List<String> matchedTags;
	private Integer matchedCount;
	private Integer totalTags;
	private Double hitRate; // 0.0 ~ 1.0
}