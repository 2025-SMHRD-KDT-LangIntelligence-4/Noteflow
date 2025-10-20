package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "battle_pass_tracks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BattlePassTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "pass_id", nullable = false)
    private Long passId;
}
