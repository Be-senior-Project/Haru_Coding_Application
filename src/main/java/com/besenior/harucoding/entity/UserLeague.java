package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.enums.LeagueTier;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_leagues",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "season"}))
@Getter
@NoArgsConstructor
public class UserLeague {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeagueTier tier = LeagueTier.BRONZE;

    @Column(nullable = false)
    private int score = 0;

    @Column(nullable = false)
    private int season;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public UserLeague(User user, LeagueTier tier, int score, int season) {
        this.user = user;
        this.tier = tier != null ? tier : LeagueTier.BRONZE;
        this.score = score;
        this.season = season;
    }
}
