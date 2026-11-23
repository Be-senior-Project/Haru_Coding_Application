package com.besenior.harucoding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 설정 전체가 정상적으로 뜨는지 확인하는 통합 테스트.
 * 실제 PostgreSQL(pgvector, init.sql 적용)이 필요해서 DB 접속 정보가 환경변수로 있을 때만 실행한다.
 * CI(.github/workflows/ci.yml)는 SPRING_DATASOURCE_URL로, 로컬에서 직접 돌릴 때는 DB_URL로 넘긴다.
 * 비밀값은 테스트 전용 값으로 고정하고, 시작 시 시드 적재(OpenAI 임베딩 호출)는 끈다.
 */
@EnabledIf("dbConfigured")
@SpringBootTest(properties = {
        "seed.autoload=false",
        "jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "encryption.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "openai.api.key=test-only-not-a-real-key"
})
class HaruCodingBackendApplicationTests {

    /** DB 접속 주소가 둘 중 하나라도 있으면 실행한다. */
    static boolean dbConfigured() {
        return isSet("SPRING_DATASOURCE_URL") || isSet("DB_URL");
    }

    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    @Test
    void contextLoads() {
    }
}
