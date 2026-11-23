package com.besenior.harucoding.global.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT 서명 키를 환경변수로 받도록 바꾼 뒤, 키가 없거나 약하면 기동을 멈추는지 검증한다. */
class JwtProviderTest {

    private JwtProperties props(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setAccessTokenExpiry(3_600_000);
        p.setRefreshTokenExpiry(604_800_000);
        return p;
    }

    @Test
    void 서명_키가_비어_있으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new JwtProvider(props("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void 서명_키가_없으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new JwtProvider(props(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 서명_키가_32바이트보다_짧으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new JwtProvider(props("x".repeat(JwtProvider.MIN_SECRET_BYTES - 1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 정상_키로_발급한_토큰은_검증되고_사용자_ID를_돌려준다() {
        JwtProvider provider = new JwtProvider(props("x".repeat(48)));

        String token = provider.generateAccessToken(7L, "user@test.com");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(7L);
    }

    @Test
    void 다른_키로_서명된_토큰은_거부한다() {
        JwtProvider issuer = new JwtProvider(props("a".repeat(48)));
        JwtProvider verifier = new JwtProvider(props("b".repeat(48)));

        String forged = issuer.generateAccessToken(1L, "attacker@test.com");

        assertThat(verifier.validateToken(forged)).isFalse();
    }
}
