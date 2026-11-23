package com.besenior.harucoding.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 호출용 HTTP 클라이언트가 응답하지 않는 서버에 막히지 않고 정한 시간 안에 실패하는지 검증한다. */
class RecommendationConfigTest {

    @Test
    void 응답이_없으면_응답_시간_제한_안에_실패한다() throws Exception {
        // 연결은 받아 두지만(운영체제 대기열) 아무 응답도 보내지 않는 로컬 서버
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            RestTemplate client = new RecommendationConfig().restTemplate(Duration.ofSeconds(2), Duration.ofMillis(500));
            String url = "http://127.0.0.1:" + silent.getLocalPort() + "/";

            long started = System.nanoTime();
            assertThatThrownBy(() -> client.getForObject(url, String.class))
                    .isInstanceOf(ResourceAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        }
    }
}
