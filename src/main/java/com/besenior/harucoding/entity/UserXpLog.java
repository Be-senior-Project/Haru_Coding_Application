package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.enums.XpReason;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_xp_logs")
@Getter
@NoArgsConstructor
public class UserXpLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int xpAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private XpReason reason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserXpLog(User user, int xpAmount, XpReason reason) {
        this.user = user;
        this.xpAmount = xpAmount;
        this.reason = reason;
    }
}
