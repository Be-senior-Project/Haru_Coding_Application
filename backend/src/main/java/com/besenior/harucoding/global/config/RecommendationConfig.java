package com.besenior.harucoding.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RecommendationConfig {

    /**
     * 외부 호출(OpenAI 문제 생성·임베딩, 구글 토큰 확인)에 쓰는 HTTP 클라이언트.
     * 시간 제한이 없으면 외부 서비스가 응답하지 않을 때 요청이 끝나지 않으므로 연결·응답 시간을 제한한다.
     * 값은 application.properties의 http.client.* 참고.
     */
    @Bean
    public RestTemplate restTemplate(@Value("${http.client.connect-timeout:5s}") Duration connectTimeout,
                                     @Value("${http.client.read-timeout:60s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
