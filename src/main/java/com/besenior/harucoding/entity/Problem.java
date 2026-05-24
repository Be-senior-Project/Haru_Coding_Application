package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.enums.DifficultyLevel;
import com.besenior.harucoding.global.enums.ProblemStyle;
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

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProblemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProblemStyle style;

    @Column(nullable = false)
    private String language = "COMMON";

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String code;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> blanks;

    @Type(JsonBinaryType.class)
    @Column(name = "match_left", columnDefinition = "jsonb")
    private List<String> matchLeft;

    @Type(JsonBinaryType.class)
    @Column(name = "match_right", columnDefinition = "jsonb")
    private List<String> matchRight;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Object answer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    private String generationModel;
    private String embeddingModel;

    // pgvector - String으로 받아서 DB에 vector 타입으로 저장
    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Problem(Topic topic, ProblemType type, DifficultyLevel difficulty,
                   ProblemStyle style, String language, String title,
                   String question, String code, List<String> options,
                   List<String> blanks, List<String> matchLeft, List<String> matchRight,
                   Object answer, String explanation,
                   String generationModel, String embeddingModel, String embedding) {
        this.topic = topic;
        this.type = type;
        this.difficulty = difficulty;
        this.style = style;
        this.language = language != null ? language : "COMMON";
        this.title = title;
        this.question = question;
        this.code = code;
        this.options = options;
        this.blanks = blanks;
        this.matchLeft = matchLeft;
        this.matchRight = matchRight;
        this.answer = answer;
        this.explanation = explanation;
        this.generationModel = generationModel;
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
    }
}
