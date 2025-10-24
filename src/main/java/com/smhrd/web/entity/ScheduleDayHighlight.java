package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "schedule_highlight",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_idx","hl_date"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleDayHighlight {

  // ✅ PK: sh_idx (AUTO_INCREMENT)
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sh_idx")
  private Long id;

  // ✅ FK: users.user_idx
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_idx", nullable = false, referencedColumnName = "user_idx")
  private User user;

  // ✅ 날짜 컬럼명 변경: hl_date
  @Column(name = "hl_date", nullable = false)
  private LocalDate date;

  // ✅ 기호: circle / star / square / triangle (ENUM은 DB, 자바는 String 유지)
  @Column(name = "symbol", nullable = false, length = 20)
  private String symbol;

  // ✅ 색상: red / yellow / blue / orange (또는 HEX 확장 가능)
  @Column(name = "color", nullable = false, length = 20)
  private String color;

  // ✅ 메모(옵션)
  @Column(name = "note", length = 300)
  private String note;

  // ✅ 타임스탬프
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
