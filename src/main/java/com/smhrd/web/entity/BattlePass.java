package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "battle_passes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BattlePass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pass_id")
    private Long passId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "season_code", nullable = false, unique = true, length = 50)
    private String seasonCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 1000;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
