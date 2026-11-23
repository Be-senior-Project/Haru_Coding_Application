package com.besenior.harucoding.generation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 사용자별 하루 AI 문제 생성 한도. */
class GenerationQuotaTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 테스트 중에 시간을 옮길 수 있는 시계. */
    private static class MovableClock extends Clock {
        private Instant now;

        MovableClock(ZonedDateTime start) {
            this.now = start.toInstant();
        }

        void moveTo(ZonedDateTime t) {
            this.now = t.toInstant();
        }

        @Override public ZoneId getZone() { return KST; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 한도_안에서는_차감하고_허용한다() {
        GenerationQuota quota = new GenerationQuota(10, Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), KST));

        assertThat(quota.tryConsume(1L, 4)).isTrue();
        assertThat(quota.tryConsume(1L, 6)).isTrue();
    }

    @Test
    void 한도를_넘는_요청은_거부하고_차감하지_않는다() {
        GenerationQuota quota = new GenerationQuota(10, Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), KST));
        quota.tryConsume(1L, 8);

        assertThat(quota.tryConsume(1L, 4)).isFalse();
        // 거부된 요청은 차감되지 않았으므로 남은 2개는 쓸 수 있다
        assertThat(quota.tryConsume(1L, 2)).isTrue();
    }

    @Test
    void 사용자별로_따로_센다() {
        GenerationQuota quota = new GenerationQuota(5, Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), KST));
        quota.tryConsume(1L, 5);

        assertThat(quota.tryConsume(2L, 5)).isTrue();
    }

    @Test
    void 한국_시간_자정이_지나면_초기화된다() {
        MovableClock clock = new MovableClock(ZonedDateTime.of(2026, 11, 22, 23, 50, 0, 0, KST));
        GenerationQuota quota = new GenerationQuota(5, clock);
        quota.tryConsume(1L, 5);
        assertThat(quota.tryConsume(1L, 1)).isFalse();

        clock.moveTo(ZonedDateTime.of(2026, 11, 23, 0, 10, 0, 0, KST));

        assertThat(quota.tryConsume(1L, 5)).isTrue();
    }
}
