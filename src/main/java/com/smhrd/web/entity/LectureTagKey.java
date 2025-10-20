package com.smhrd.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LectureTagKey implements Serializable {

    @Column(name = "lec_idx")
    private Long lecIdx;

    @Column(name = "tag_idx")
    private Long tagIdx;
}
