package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
  name = "day_highlight",
  uniqueConstraints = @UniqueConstraint(columnNames = {"user_idx","hl_date"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleDayHighlight {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_idx", nullable = false)
  private User user;

  @Column(name = "hl_date", nullable = false)
  private LocalDate date;

  // 기호: circle / star / square / triangle
  @Column(name = "symbol", nullable = false, length = 20)
  private String symbol;

  // 색상: red / yellow / blue / orange (혹은 HEX)
  @Column(name = "color", nullable = false, length = 20)
  private String color;

  // 메모/툴팁용 (옵션)
  @Column(name = "note", length = 300)
  private String note;

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist void onCreate(){ createdAt = updatedAt = LocalDateTime.now(); }
  @PreUpdate  void onUpdate(){ updatedAt = LocalDateTime.now(); }
}
