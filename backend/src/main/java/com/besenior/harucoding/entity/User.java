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

    // recommended_difficulty 컬럼은 ERD 유지를 위해 DB에 남겨두되, 서버는 읽지도 쓰지도 않는다.
    // coding_level + cote_prepared로 언제든 다시 계산되는 값이라(RecommendationService.onboardingBaseLevel)
    // 따로 저장해두면 두 값이 어긋날 뿐이다. 그래서 엔티티에서 필드를 뺐다.

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

    public void updateProfile(String nickname, String preferredLanguage, String fcmToken) {
        if (nickname != null) this.nickname = nickname;
        if (preferredLanguage != null) this.preferredLanguage = preferredLanguage;
        if (fcmToken != null) this.fcmToken = fcmToken;
    }

    /** 온보딩 답변 기록. 가입이 확정된 뒤에만 호출된다. */
    public void updateOnboarding(String codingLevel, boolean cotePrepared) {
        this.codingLevel = codingLevel;
        this.cotePrepared = cotePrepared;
    }
}