package com.besenior.harucoding.service;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 앱에서 받은 구글 ID 토큰을 검증한다.
 *
 * tokeninfo는 토큰이 구글이 발급한 진짜인지만 확인해 주고, 어느 앱용인지는 확인하지 않는다.
 * 발급 대상(aud)을 우리 클라이언트 ID와 비교하지 않으면 다른 앱용으로 발급된 토큰으로도
 * 그 사람 계정으로 로그인할 수 있으므로 aud·iss·이메일 인증 여부를 직접 확인한다.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private static final Set<String> GOOGLE_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestTemplate restTemplate;
    private final Set<String> allowedClientIds;

    /**
     * @param clientIds 허용할 구글 클라이언트 ID. 쉼표로 여러 개 지정 가능(웹·안드로이드 등).
     *                  앱이 webClientId로 로그인하므로 프론트 GOOGLE_WEB_CLIENT_ID와 같은 값이어야 한다.
     */
    public GoogleTokenVerifier(
            RestTemplate restTemplate,
            @Value("${google.client-ids:}") String clientIds) {
        this.restTemplate = restTemplate;
        this.allowedClientIds = clientIds == null ? Set.of()
                : Arrays.stream(clientIds.split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (allowedClientIds.isEmpty()) {
            log.warn("구글 클라이언트 ID가 설정되지 않아 구글 로그인을 모두 거부합니다 (GOOGLE_CLIENT_ID 확인)");
        }
    }

    public GoogleUserInfo verify(String idToken) {
        if (allowedClientIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Map<?, ?> info;
        try {
            info = restTemplate.getForObject(GOOGLE_TOKEN_INFO_URL, Map.class, idToken);
        } catch (Exception e) {
            // 만료·위조된 토큰이면 tokeninfo가 400을 돌려준다
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        if (info == null
                || !allowedClientIds.contains(text(info.get("aud")))
                || !GOOGLE_ISSUERS.contains(text(info.get("iss")))
                || !"true".equals(text(info.get("email_verified")))) {
            log.info("구글 토큰 거부: aud/iss/email_verified 불일치");
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        return new GoogleUserInfo(
                text(info.get("sub")),
                text(info.get("email")),
                text(info.get("name")),
                text(info.get("picture"))
        );
    }

    /** tokeninfo는 값을 문자열로 주지만, email_verified처럼 불리언으로 올 때도 같은 문자열로 맞춘다. */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record GoogleUserInfo(String googleId, String email, String name, String picture) {}
}
