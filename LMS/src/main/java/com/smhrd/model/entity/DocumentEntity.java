package com.smhrd.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity // DB 테이블 맵핑용
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본키 자동생성
    private Long id;

    private String fileName;          // 업로드한 파일명

    private String filePath;          // 로컬 저장 경로

    @Column(columnDefinition = "TEXT")	// 긴 텍스트 저장 가능
    private String content;           // 문서 내용 (추출 시 저장 가능)

    private LocalDateTime uploadTime; // 업로드 시간
}
