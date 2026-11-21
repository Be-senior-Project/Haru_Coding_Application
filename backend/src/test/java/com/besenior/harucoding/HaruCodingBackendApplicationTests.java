package com.besenior.harucoding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 설정 전체가 정상적으로 뜨는지 확인하는 통합 테스트.
 * 실제 PostgreSQL(pgvector, init.sql 적용)이 필요해서 DB 접속 정보(DB_URL·DB_USERNAME·DB_PASSWORD 환경변수)가
 * 있을 때만 실행한다. CI에서 돌리려면 DB 서비스 컨테이너를 띄우고 이 환경변수를 넘기면 된다.
 * 비밀값은 테스트 전용 값으로 고정하고, 시작 시 시드 적재(OpenAI 임베딩 호출)는 끈다.
 */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "seed.autoload=false",
        "jwt.secret=test-only-jwt-secret-at-least-32-bytes-long",
        "encryption.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "openai.api.key=test-only-not-a-real-key"
})
class HaruCodingBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
