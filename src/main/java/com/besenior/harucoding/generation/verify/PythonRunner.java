package com.besenior.harucoding.generation.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Python 검증기.
 * 조립: import json / {answerCode} / {ioInput} / print(json.dumps(solution(args), ensure_ascii=False))
 */
public class PythonRunner implements Runner {

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
        String program = "import json\n" + answerCode + "\n\n" + ioInput + "\n"
                + "print(json.dumps(solution(" + args + "), ensure_ascii=False))\n";

        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("verify_py");
            Path script = tmp.resolve("solution.py");
            Files.writeString(script, program);

            ProcessUtils.Result r = ProcessUtils.run(
                    List.of("python3", script.toString()), tmp.toFile(), EXEC_TIMEOUT);

            if (r.timedOut()) {
                return VerifyResult.fail("timeout", "실행 시간 " + EXEC_TIMEOUT + "초 초과");
            }
            if (r.exit() != 0) {
                return VerifyResult.fail("runtime_error", RunnerSupport.blankToDefault(r.stderr(), "non-zero exit code"));
            }
            String actual = r.stdout().strip();
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
