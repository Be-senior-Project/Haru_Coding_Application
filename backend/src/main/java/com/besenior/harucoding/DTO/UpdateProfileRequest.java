package com.besenior.harucoding.DTO;

import lombok.Getter;

@Getter
public class UpdateProfileRequest {
    private String nickname;
    private String preferredLanguage;
    private String fcmToken;
    private Integer dailyGoalCount;
    private String difficultyLevel; // "AUTO" | "L1".."L5"
}