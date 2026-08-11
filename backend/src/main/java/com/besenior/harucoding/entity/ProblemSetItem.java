package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "problem_set_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"problem_set_id", "problem_id"}))
@Getter
@NoArgsConstructor
public class ProblemSetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_set_id", nullable = false)
    private ProblemSet problemSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Builder
    public ProblemSetItem(ProblemSet problemSet, Problem problem, int sortOrder) {
        this.problemSet = problemSet;
        this.problem = problem;
        this.sortOrder = sortOrder;
    }
}
