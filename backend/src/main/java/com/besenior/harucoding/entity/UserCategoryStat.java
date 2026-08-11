package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_category_stats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "topic_id"}))
@Getter
@NoArgsConstructor
public class UserCategoryStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private int totalSolved = 0;

    @Column(nullable = false)
    private int correctCount = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public UserCategoryStat(User user, Topic topic) {
        this.user = user;
        this.topic = topic;
    }

    public void recordResult(boolean isCorrect) {
        this.totalSolved++;
        if (isCorrect) this.correctCount++;
    }

    public double getAccuracyRate() {
        if (totalSolved == 0) return 0.0;
        return (double) correctCount / totalSolved * 100;
    }
}
