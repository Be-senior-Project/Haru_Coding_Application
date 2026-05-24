package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user_streak_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "streak_date"}))
@Getter
@NoArgsConstructor
public class UserStreakLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate streakDate;

    @Column(nullable = false)
    private int streakCount = 1;

    @Builder
    public UserStreakLog(User user, LocalDate streakDate, int streakCount) {
        this.user = user;
        this.streakDate = streakDate;
        this.streakCount = streakCount;
    }
}
