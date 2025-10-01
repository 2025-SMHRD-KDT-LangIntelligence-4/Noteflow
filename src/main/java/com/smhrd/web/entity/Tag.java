package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tagIdx;

    @Column(nullable = false, unique = true)
    private String name;
}
