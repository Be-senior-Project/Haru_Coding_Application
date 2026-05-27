package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {
    private Long id;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private int level;
    private int xp;
    private int streakDays;
    private long totalSolved;
    private long correctCount;
    private double accuracyRate;
    private String preferredLanguage;

    public static UserProfileResponse from(User user, long totalSolved, long correctCount) {
        double accuracy = totalSolved == 0 ? 0.0
                : Math.round((double) correctCount / totalSolved * 1000) / 10.0;
        return UserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .level(user.getLevel())
                .xp(user.getXp())
                .streakDays(user.getStreakDays())
                .totalSolved(totalSolved)
                .correctCount(correctCount)
                .accuracyRate(accuracy)
                .preferredLanguage(user.getPreferredLanguage())
                .build();
    }
}