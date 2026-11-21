package com.besenior.harucoding.generation.verify;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 사용자 코드 실행 격리: 비밀 환경변수 차단, 격리 명령 구성, 출력 크기 제한. */
class ProcessUtilsTest {

    @Test
    void 비밀_환경변수는_자식_프로세스에_넘기지_않는다() {
        Map<String, String> parent = Map.of(
                "PATH", "/usr/bin",
                "OPENAI_API_KEY", "sk-test",
                "ENCRYPTION_SECRET_KEY", "enc",
                "JWT_SECRET", "jwt",
                "SPRING_DATASOURCE_PASSWORD", "db");

        Map<String, String> env = ProcessUtils.childEnvironment(parent, new File("/tmp/work"));

        assertThat(env).containsEntry("PATH", "/usr/bin");
        assertThat(env).doesNotContainKeys(
                "OPENAI_API_KEY", "ENCRYPTION_SECRET_KEY", "JWT_SECRET", "SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    void 허용된_환경변수는_대소문자와_무관하게_넘긴다() {
        Map<String, String> env = ProcessUtils.childEnvironment(
                Map.of("Path", "C:\\Windows", "SystemRoot", "C:\\Windows"), null);

        assertThat(env).containsEntry("Path", "C:\\Windows");
        assertThat(env).containsEntry("SystemRoot", "C:\\Windows");
    }

    @Test
    void 홈과_임시_폴더는_작업_폴더로_돌리고_인코딩은_UTF8로_고정한다() {
        File work = new File("/tmp/work");

        Map<String, String> env = ProcessUtils.childEnvironment(Map.of(), work);

        assertThat(env).containsEntry("HOME", work.getAbsolutePath());
        assertThat(env).containsEntry("TMPDIR", work.getAbsolutePath());
        assertThat(env).containsEntry("PYTHONIOENCODING", "utf-8");
    }

    @Test
    void 격리_계정이_없으면_명령을_그대로_실행한다() {
        List<String> cmd = List.of("python3", "solution.py");

        assertThat(ProcessUtils.sandboxed(cmd, null)).isEqualTo(cmd);
    }

    @Test
    void 격리_계정이_있으면_자원_제한과_계정_전환으로_감싼다() {
        List<String> wrapped = ProcessUtils.sandboxed(List.of("python3", "solution.py"), "coderunner");

        assertThat(wrapped.get(0)).isEqualTo("prlimit");
        assertThat(wrapped).contains("setpriv", "--reuid=coderunner", "--regid=coderunner",
                "--clear-groups", "--no-new-privs");
        // 실제 명령은 맨 끝에, 원래 순서 그대로 붙어야 한다
        assertThat(wrapped.subList(wrapped.size() - 3, wrapped.size()))
                .containsExactly("--", "python3", "solution.py");
    }

    @Test
    void 출력이_한도를_넘으면_잘라내고_표시를_붙인다() {
        String big = "a".repeat(100);

        String read = ProcessUtils.readCapped(
                new ByteArrayInputStream(big.getBytes(StandardCharsets.UTF_8)), 10);

        assertThat(read).startsWith("a".repeat(10));
        assertThat(read).endsWith(ProcessUtils.TRUNCATED_MARK);
    }

    @Test
    void 출력이_한도_이내면_그대로_돌려준다() {
        String read = ProcessUtils.readCapped(
                new ByteArrayInputStream("정답 15".getBytes(StandardCharsets.UTF_8)), 100);

        assertThat(read).isEqualTo("정답 15");
    }
}
