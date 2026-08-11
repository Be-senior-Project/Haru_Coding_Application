package com.besenior.harucoding.DTO;

import lombok.Getter;

@Getter
public class UpdateProfileRequest {
    private String nickname;
    private String preferredLanguage;
    private String fcmToken;
}