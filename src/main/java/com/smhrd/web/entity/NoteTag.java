package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "note_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noteTagIdx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_idx", nullable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_idx", nullable = false)
    private Tag tag;
}
