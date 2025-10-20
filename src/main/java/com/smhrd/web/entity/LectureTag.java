package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lecture_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureTag {

    @EmbeddedId
    private LectureTagKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("lecIdx") // LectureTagKey.lecIdx와 매핑
    @JoinColumn(name = "lec_idx", nullable = false)
    private Lecture lecture;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagIdx") // LectureTagKey.tagIdx와 매핑
    @JoinColumn(name = "tag_idx", nullable = false)
    private Tag tag;
}
