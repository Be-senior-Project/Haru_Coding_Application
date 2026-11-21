package com.besenior.harucoding.service;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 구글 ID 토큰의 발급 대상(aud)·발급자(iss)·이메일 인증 여부 검증. */
@ExtendWith(MockitoExtension.class)
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "web-client.apps.googleusercontent.com";
    private static final String TOKEN = "id-token";

    @Mock private RestTemplate restTemplate;

    private Map<String, Object> tokenInfo(String aud, String iss, Object emailVerified) {
        Map<String, Object> m = new HashMap<>();
        m.put("aud", aud);
        m.put("iss", iss);
        m.put("email_verified", emailVerified);
        m.put("sub", "google-123");
        m.put("email", "user@gmail.com");
        m.put("name", "사용자");
        m.put("picture", "https://example.com/p.png");
        return m;
    }

    private void tokenInfoReturns(Map<String, Object> info) {
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq(TOKEN))).thenReturn(info);
    }

    private void assertRejected(GoogleTokenVerifier verifier) {
        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void 우리_앱용으로_발급된_토큰이면_사용자_정보를_돌려준다() {
        tokenInfoReturns(tokenInfo(CLIENT_ID, "https://accounts.google.com", "true"));

        GoogleTokenVerifier.GoogleUserInfo user = new GoogleTokenVerifier(restTemplate, CLIENT_ID).verify(TOKEN);

        assertThat(user.googleId()).isEqualTo("google-123");
        assertThat(user.email()).isEqualTo("user@gmail.com");
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_거부한다() {
        tokenInfoReturns(tokenInfo("other-app.apps.googleusercontent.com", "accounts.google.com", "true"));

        assertRejected(new GoogleTokenVerifier(restTemplate, CLIENT_ID));
    }

    @Test
    void 이메일이_인증되지_않은_토큰은_거부한다() {
        tokenInfoReturns(tokenInfo(CLIENT_ID, "accounts.google.com", "false"));

        assertRejected(new GoogleTokenVerifier(restTemplate, CLIENT_ID));
    }

    @Test
    void 발급자가_구글이_아니면_거부한다() {
        tokenInfoReturns(tokenInfo(CLIENT_ID, "https://evil.example.com", "true"));

        assertRejected(new GoogleTokenVerifier(restTemplate, CLIENT_ID));
    }

    @Test
    void 만료_위조된_토큰이라_tokeninfo가_실패하면_거부한다() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), eq(TOKEN)))
                .thenThrow(new RestClientException("400 Bad Request"));

        assertRejected(new GoogleTokenVerifier(restTemplate, CLIENT_ID));
    }

    @Test
    void 클라이언트_ID가_설정되지_않으면_구글에_묻지도_않고_거부한다() {
        assertRejected(new GoogleTokenVerifier(restTemplate, ""));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void 쉼표로_지정한_여러_클라이언트_ID를_모두_허용한다() {
        tokenInfoReturns(tokenInfo("android-client", "accounts.google.com", Boolean.TRUE));

        GoogleTokenVerifier verifier = new GoogleTokenVerifier(restTemplate, CLIENT_ID + ", android-client");

        assertThat(verifier.verify(TOKEN).googleId()).isEqualTo("google-123");
    }
}
