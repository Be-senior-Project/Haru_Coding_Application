package com.besenior.harucoding.generation.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Java 검증기. resources/runner/java_template.txt 를 로드해 조립 후 javac/java 실행.
 * 반환값은 __json 헬퍼로 JSON 직렬화해 출력 → Python json.dumps 형식과 일치시킨다.
 */
public class JavaRunner implements Runner {

    private static final int COMPILE_TIMEOUT = 15;
    private static final int EXEC_TIMEOUT = 5;

    @Override
    public VerifyResult run(String answerCode, String ioInput, String expectedOutput, String signature) {
        List<String> params;
        try {
            params = SignatureParser.extractParamNames(signature);
        } catch (Exception e) {
            return VerifyResult.fail("internal_error", "Signature 파싱 실패: " + e.getMessage());
        }
        String args = String.join(", ", params);

        String program = RunnerSupport.loadTemplate("runner/java_template.txt")
                .replace("__ANSWER__", RunnerSupport.indentBody(answerCode, 4))
                .replace("__IO__", RunnerSupport.indentBody(ioInput, 8))
                .replace("__ARGS__", args);

        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("verify_java");
            Path src = tmp.resolve("Main.java");
            Files.writeString(src, program);

            // 동시에 여러 사용자의 코드가 컴파일·실행되므로 JVM 하나가 서버 메모리를 다 쓰지 못하게 상한을 둔다.
            // 실행 환경변수를 비우면서 기본 인코딩이 바뀌지 않도록 UTF-8도 명시한다.
            ProcessUtils.Result compile = ProcessUtils.run(
                    List.of("javac", "-encoding", "UTF-8", "-J-Xmx256m", "-J-XX:+UseSerialGC", src.toString()),
                    tmp.toFile(), COMPILE_TIMEOUT);
            if (compile.timedOut()) {
                return VerifyResult.fail("compile_error", "컴파일 시간 " + COMPILE_TIMEOUT + "초 초과");
            }
            if (compile.exit() != 0) {
                return VerifyResult.fail("compile_error", RunnerSupport.blankToDefault(compile.stderr(), "javac failed"));
            }

            ProcessUtils.Result exec = ProcessUtils.run(
                    List.of("java", "-Xmx256m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8",
                            "-cp", tmp.toString(), "Main"),
                    tmp.toFile(), EXEC_TIMEOUT);
            if (exec.timedOut()) {
                return VerifyResult.fail("timeout", "실행 시간 " + EXEC_TIMEOUT + "초 초과");
            }
            if (exec.exit() != 0) {
                return VerifyResult.fail("runtime_error", RunnerSupport.blankToDefault(exec.stderr(), "non-zero exit code"));
            }
            String actual = exec.stdout().strip();
            String expected = expectedOutput.strip();
            if (!actual.equals(expected)) {
                return VerifyResult.fail("output_mismatch", "expected: " + expected + ", actual: " + actual, actual);
            }
            return VerifyResult.pass(actual);
        } catch (Exception e) {
            return VerifyResult.fail("internal_error", "실행 오류: " + e.getMessage());
        } finally {
            RunnerSupport.deleteQuietly(tmp);
        }
    }
}
