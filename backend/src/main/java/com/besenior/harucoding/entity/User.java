package com.besenior.harucoding.entity;

import com.besenior.harucoding.global.crypto.EmailCryptoConverter;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor

public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String googleId;

    @Convert(converter = EmailCryptoConverter.class)
    @Column(nullable = false)
    private String email;

    @Column(name = "email_hash", nullable = false, unique = true)
    private String emailHash;

    private String password;

    private String nickname;

    private String profileImageUrl;

    @Column(nullable = false)
    private int level = 1;

    @Column(nullable = false)
    private int xp = 0;

    @Column(nullable = false)
    private int streakDays = 0;

    // 팀원 ERD + init.sql에 있는 필드 추가
    private String preferredLanguage;

    // 온보딩 역량 정보
    @Column(length = 10)
    private String codingLevel = "NONE";         // NONE / SOME / LOTS

    @Column
    private boolean cotePrepared = false;

    @Column(length = 10)
    private String recommendedDifficulty;  // 추천 결과 저장

    private String fcmToken;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public User(String googleId, String email, String emailHash, String password,
                String nickname, String profileImageUrl, String preferredLanguage, String fcmToken) {
        this.googleId = googleId;
        this.email = email;
        this.emailHash = emailHash;
        this.password = password;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.preferredLanguage = preferredLanguage;
        this.fcmToken = fcmToken;
        this.level = 1;
        this.xp = 0;
        this.streakDays = 0;
        this.codingLevel = "NONE";
        this.cotePrepared = false;
    }

    public void updateProfile(String nickname, String preferredLanguage, String fcmToken, String recommendedDifficulty) {
        if (nickname != null) this.nickname = nickname;
        if (preferredLanguage != null) this.preferredLanguage = preferredLanguage;
        if (fcmToken != null) this.fcmToken = fcmToken;
        this.recommendedDifficulty = recommendedDifficulty;
    }

    public void updateOnboarding(String codingLevel,
                                 boolean cotePrepared,
                                 String recommendedDifficulty) {
        this.codingLevel = codingLevel;
        this.cotePrepared = cotePrepared;
        this.recommendedDifficulty = recommendedDifficulty;
    }
}