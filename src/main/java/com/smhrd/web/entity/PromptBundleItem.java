package com.smhrd.web.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prompt_bundle_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bundle_id", "prompt_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptBundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_id", nullable = false)
    private Long bundleId;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;
}
