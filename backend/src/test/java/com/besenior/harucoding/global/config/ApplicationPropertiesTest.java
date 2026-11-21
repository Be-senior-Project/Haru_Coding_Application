package com.besenior.harucoding.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** 환경변수 이름 통일 후에도 팀원의 기존 .env(예전 이름)가 그대로 동작하는지, 운영 프로필 파일이 읽히는지 검증한다. */
class ApplicationPropertiesTest {

    private String resolve(String file, String key, Map<String, Object> env) throws IOException {
        Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource(file));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("env", env));
        return new PropertySourcesPropertyResolver(sources).resolvePlaceholders(props.getProperty(key));
    }

    @Test
    void 예전_DB_환경변수_이름도_인식한다() throws IOException {
        Map<String, Object> env = Map.of(
                "Haru_Coding_backend_DATASOURCE_URL", "jdbc:postgresql://old:5432/db",
                "Haru_Coding_backend_DATASOURCE_PASSWORD", "old-pw");

        assertThat(resolve("application.properties", "spring.datasource.url", env))
                .isEqualTo("jdbc:postgresql://old:5432/db");
        assertThat(resolve("application.properties", "spring.datasource.password", env)).isEqualTo("old-pw");
    }

    @Test
    void 표준_이름과_예전_이름이_둘_다_있으면_표준_이름을_쓴다() throws IOException {
        Map<String, Object> env = Map.of(
                "DB_URL", "jdbc:postgresql://new:5432/db",
                "Haru_Coding_backend_DATASOURCE_URL", "jdbc:postgresql://old:5432/db");

        assertThat(resolve("application.properties", "spring.datasource.url", env))
                .isEqualTo("jdbc:postgresql://new:5432/db");
    }

    @Test
    void 구글_클라이언트_ID도_예전_이름을_인식한다() throws IOException {
        assertThat(resolve("application.properties", "google.client-ids",
                Map.of("Haru_Coding_backend_CLIENT_ID", "old-client"))).isEqualTo("old-client");
        assertThat(resolve("application.properties", "google.client-ids",
                Map.of("GOOGLE_CLIENT_ID", "new-client"))).isEqualTo("new-client");
    }

    @Test
    void 운영_프로필_파일은_Spring_규칙_이름으로_존재하고_SQL_로그를_끈다() throws IOException {
        Properties prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

        assertThat(prod.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(new ClassPathResource("application.properties.prod").exists()).isFalse();
    }
}
