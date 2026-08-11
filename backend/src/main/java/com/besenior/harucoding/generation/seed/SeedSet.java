package com.besenior.harucoding.generation.seed;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

/**
 * 시드 세트 (RAG few-shot 전용, 유저에게 서빙되지 않음).
 * 개념 임베딩으로 "비슷한 개념 세트"를 검색해 생성 프롬프트의 예시로 사용한다.
 * embedding은 pgvector 벡터를 문자열 리터럴("[0.1, 0.2, ...]")로 저장.
 */
@Entity
@Table(name = "seed_sets")
@Getter
@NoArgsConstructor
public class SeedSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    private String subcategory;

    @Column(nullable = false)
    private int difficulty;   // 0, 1, 2

    @Column(nullable = false)
    private String language;  // Python, Java, C++

    @Column(name = "concept_explanation", columnDefinition = "TEXT")
    private String conceptExplanation;

    /** 세트의 문제 배열(원본 JSON). few-shot 예시 구성에 사용. */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> problems;

    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    @Builder
    public SeedSet(String category, String subcategory, int difficulty, String language,
                   String conceptExplanation, List<Map<String, Object>> problems, String embedding) {
        this.category = category;
        this.subcategory = subcategory;
        this.difficulty = difficulty;
        this.language = language;
        this.conceptExplanation = conceptExplanation;
        this.problems = problems;
        this.embedding = embedding;
    }
}
