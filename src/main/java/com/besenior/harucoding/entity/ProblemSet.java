package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.enums.DifficultyLevel;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problem_sets")
@Getter
@NoArgsConstructor
public class ProblemSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty = DifficultyLevel.초급;

    @Column(nullable = false)
    private boolean isAiGenerated = false;

    @OneToMany(mappedBy = "problemSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProblemSetItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ProblemSet(String title, LocalDate targetDate,
                      DifficultyLevel difficulty, boolean isAiGenerated) {
        this.title = title;
        this.targetDate = targetDate;
        this.difficulty = difficulty != null ? difficulty : DifficultyLevel.초급;
        this.isAiGenerated = isAiGenerated;
    }
}
