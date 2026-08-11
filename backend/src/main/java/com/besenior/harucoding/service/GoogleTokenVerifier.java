package com.besenior.harucoding.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleUserInfo verify(String idToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> response = restTemplate.getForObject(
                    GOOGLE_TOKEN_INFO_URL, Map.class, idToken);

            if (response == null) {
                throw new RuntimeException("유효하지 않은 Google 토큰");
            }

            return new GoogleUserInfo(
                    response.get("sub"),
                    response.get("email"),
                    response.get("name"),
                    response.get("picture")
            );
        } catch (Exception e) {
            throw new RuntimeException("유효하지 않은 Google 토큰");
        }
    }

    public record GoogleUserInfo(String googleId, String email, String name, String picture) {}
}
