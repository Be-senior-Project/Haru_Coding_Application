package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 추천용 문제 임베딩. problems 테이블(확정)과 분리해 별도 저장한다.
 * embedding은 pgvector 벡터를 문자열 리터럴("[0.1, 0.2, ...]")로 주고받는다.
 */
@Entity
@Table(name = "problem_embeddings")
@Getter
@NoArgsConstructor
public class ProblemEmbedding {

    @Id
    private Long problemId;   // = problems.id (직접 부여)

    @Column(columnDefinition = "vector(1536)", nullable = false)
    private String embedding;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ProblemEmbedding(Long problemId, String embedding) {
        this.problemId = problemId;
        this.embedding = embedding;
    }
}
