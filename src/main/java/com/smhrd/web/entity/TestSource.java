package com.smhrd.web.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "test_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_source_idx")
    private Long testSourceIdx;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 10)
    private String difficulty;

    @Column(name = "category_large", length = 100)
    private String categoryLarge;

    @Column(name = "category_medium", length = 100)
    private String categoryMedium;

    @Column(name = "category_small", length = 100)
    private String categorySmall;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(name = "options", columnDefinition = "TEXT")
    private String optionsJson;  // DB 원본 문자열

    // ⭐ Getter에서 JSON 파싱 ⭐
    @Transient
    public List<String> getOptions() {
        if (optionsJson == null || optionsJson.isEmpty() || "[]".equals(optionsJson)) {
            return Collections.emptyList();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(optionsJson, new TypeReference<List<String>>(){});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    public void setOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            this.optionsJson = "[]";
        } else {
            try {
                ObjectMapper mapper = new ObjectMapper();
                this.optionsJson = mapper.writeValueAsString(options);
            } catch (JsonProcessingException e) {
                this.optionsJson = "[]";
            }
        }
    }


    @Column(name = "created_at", nullable = false, updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
