package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditWallet {

    @Id
    @Column(name = "user_idx")
    private Long userIdx;

    @Column(name = "credit", nullable = false)
    private Integer credit = 0;

    @Column(name = "golden", nullable = false)
    private Integer golden = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
