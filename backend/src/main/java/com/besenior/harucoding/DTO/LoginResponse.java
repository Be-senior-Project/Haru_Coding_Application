package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
}
