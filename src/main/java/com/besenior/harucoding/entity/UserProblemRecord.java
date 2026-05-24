package com.besenior.harucoding.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_problem_records")
@Getter
@NoArgsConstructor
public class UserProblemRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private boolean isCorrect;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Object userAnswer;

    private Integer timeSpentSec;

    @Column(nullable = false)
    private int xpEarned = 0;

    @CreationTimestamp
    @Column(name = "solved_at", updatable = false)
    private LocalDateTime solvedAt;

    @Builder
    public UserProblemRecord(User user, Problem problem, boolean isCorrect,
                             Object userAnswer, Integer timeSpentSec, int xpEarned) {
        this.user = user;
        this.problem = problem;
        this.isCorrect = isCorrect;
        this.userAnswer = userAnswer;
        this.timeSpentSec = timeSpentSec;
        this.xpEarned = xpEarned;
    }
}
