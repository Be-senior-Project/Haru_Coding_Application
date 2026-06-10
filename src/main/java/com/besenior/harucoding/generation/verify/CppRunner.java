package com.besenior.harucoding.generation.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * C++ 검증기. resources/runner/cpp_template.txt 를 로드해 조립 후 g++ 컴파일/실행.
 * 반환값은 __json 오버로드로 JSON 직렬화해 출력 → Python json.dumps 형식과 일치시킨다.
 */
public class CppRunner implements Runner {

    private static final int COMPILE_TIMEOUT = 20;
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

        String program = RunnerSupport.loadTemplate("runner/cpp_template.txt")
                .replace("__ANSWER__", answerCode)
                .replace("__IO__", RunnerSupport.indentBody(ioInput, 4))
                .replace("__ARGS__", args);

        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("verify_cpp");
            Path src = tmp.resolve("solution.cpp");
            Path bin = tmp.resolve("solution");
            Files.writeString(src, program);

            ProcessUtils.Result compile = ProcessUtils.run(
                    List.of("g++", "-std=c++17", "-O2", "-o", bin.toString(), src.toString()),
                    tmp.toFile(), COMPILE_TIMEOUT);
            if (compile.timedOut()) {
                return VerifyResult.fail("compile_error", "컴파일 시간 " + COMPILE_TIMEOUT + "초 초과");
            }
            if (compile.exit() != 0) {
                return VerifyResult.fail("compile_error", RunnerSupport.blankToDefault(compile.stderr(), "g++ failed"));
            }

            ProcessUtils.Result exec = ProcessUtils.run(
                    List.of(bin.toString()), tmp.toFile(), EXEC_TIMEOUT);
            if (exec.timedOut()) {
                return VerifyResult.fail("timeout", "실행 시간 " + EXEC_TIMEOUT + "초 초과");
            }
            if (exec.exit() != 0) {
                return VerifyResult.fail("runtime_error", RunnerSupport.blankToDefault(exec.stderr(), "non-zero exit code"));
            }
            String actual = exec.stdout().strip();
            String expected = expectedOutput.strip();
            if (!actual.equals(expected)) {
                return VerifyResult.fail("output_mismatch", "expected: " + expected + ", actual: " + actual);
            }
            return VerifyResult.pass();
        } catch (Exception e) {
            return VerifyResult.fail("internal_error", "실행 오류: " + e.getMessage());
        } finally {
            RunnerSupport.deleteQuietly(tmp);
        }
    }
}
