package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "learning_statistics", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_idx", "stat_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "notes_created", nullable = false)
    private Integer notesCreated = 0;

    @Column(name = "tests_taken", nullable = false)
    private Integer testsTaken = 0;

    @Column(name = "study_minutes", nullable = false)
    private Integer studyMinutes = 0;

    @Column(name = "ai_questions", nullable = false)
    private Integer aiQuestions = 0;
}
