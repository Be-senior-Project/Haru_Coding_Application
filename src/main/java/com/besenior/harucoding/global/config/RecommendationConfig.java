package com.besenior.harucoding.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RecommendationConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
