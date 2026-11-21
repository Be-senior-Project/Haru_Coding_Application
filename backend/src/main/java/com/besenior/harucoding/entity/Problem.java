package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.enums.ProblemType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // seed의 "Set Id" (예: SET-BI-01) — set 테이블과의 느슨한 연결용
    @Column(name = "set_id")
    private String setId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProblemType type;

    // 분류 (seed의 set 단위 정보를 문제 행에 보존)
    @Column(nullable = false)
    private String category;

    private String subcategory;

    @Column(nullable = false)
    private int difficulty;   // 0, 1, 2

    @Column(nullable = false)
    private String language;   // Python, Java, C++

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> constraints;

    // IO Example: {"input": "...", "output": "..."}
    @Type(JsonBinaryType.class)
    @Column(name = "io_example", columnDefinition = "jsonb")
    private Map<String, String> ioExample;

    @Column(name = "code_skeleton", columnDefinition = "TEXT")
    private String codeSkeleton;

    // Answer는 문자열(Implementation/Debugging) 또는 문자열 배열(Fill-in-the-blank)
    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Object answer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    // seed의 set 단위 "Concept Explanation" 보존
    @Column(name = "concept_explanation", columnDefinition = "TEXT")
    private String conceptExplanation;

    // 숨겨진 테스트: [{"input": 변수 선언문, "output": 기대 출력}]. 채점에만 쓰고 조회 응답(ProblemResponse)에는 넣지 않는다.
    // 예시 출력만 그대로 출력하는 풀이를 걸러내기 위한 것이라, 앱에 노출되면 의미가 없어진다.
    @Type(JsonBinaryType.class)
    @Column(name = "hidden_tests", columnDefinition = "jsonb")
    private List<Map<String, String>> hiddenTests;

    @Column(columnDefinition = "TEXT")
    private String hint1;

    @Column(columnDefinition = "TEXT")
    private String hint2;

    @Column(columnDefinition = "TEXT")
    private String hint3;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Problem(String setId, ProblemType type, String category, String subcategory,
                   int difficulty, String language, String title, String description,
                   List<String> constraints, Map<String, String> ioExample,
                   String codeSkeleton, Object answer, String explanation,
                   String conceptExplanation, List<Map<String, String>> hiddenTests,
                   String hint1, String hint2, String hint3) {
        this.setId = setId;
        this.type = type;
        this.category = category;
        this.subcategory = subcategory;
        this.difficulty = difficulty;
        this.language = language;
        this.title = title;
        this.description = description;
        this.constraints = constraints;
        this.ioExample = ioExample;
        this.codeSkeleton = codeSkeleton;
        this.answer = answer;
        this.explanation = explanation;
        this.conceptExplanation = conceptExplanation;
        this.hiddenTests = hiddenTests;
        this.hint1 = hint1;
        this.hint2 = hint2;
        this.hint3 = hint3;
    }
}
