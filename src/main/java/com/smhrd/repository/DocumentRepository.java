package com.smhrd.repository;

import com.smhrd.model.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// DB와 CRUD 작업 담당
// JpaRepository 상속 → 기본 CRUD 메서드 제공 (save, findById, findAll, delete 등)
// Repository는 Service가 DB에 접근할 때 사용
@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    // 나중에 키워드 검색 메서드 추가 가능
}
