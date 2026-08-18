package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자가 스크랩(북마크)한 문제. 문제은행 탭의 "스크랩" 목록에 노출된다.
 * (user_id, problem_id) 유니크 — 같은 문제를 두 번 스크랩할 수 없다.
 */
@Entity
@Table(
        name = "problem_scraps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_scraps_user_problem",
                columnNames = {"user_id", "problem_id"}
        )
)
@Getter
@NoArgsConstructor
public class ProblemScrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ProblemScrap(User user, Problem problem) {
        this.user = user;
        this.problem = problem;
    }
}
