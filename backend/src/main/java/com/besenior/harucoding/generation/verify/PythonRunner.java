package com.besenior.harucoding.generation.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Python 검증기.
 * 조립: import json / {answerCode} / {ioInput} / print(json.dumps(solution(args), ensure_ascii=False))
 */
public class PythonRunner implements Runner {

    private static final int EXEC_TIMEOUT = 5;
    private static final int PROBE_TIMEOUT = 5;

    /** 인터프리터 이름은 OS마다 다르다. macOS·Linux는 python3, Windows 설치본은 대개 python 또는 py. */
    private static final List<String> PYTHON_CANDIDATES = List.of("python3", "python", "py");

    /**
     * "Python 3.12.3"처럼 버전이 찍혀야 진짜다.
     * Windows에는 python3라는 이름의 Microsoft Store 안내용 껍데기가 PATH에 있는데,
     * 이 껍데기도 "Python was not found..."를 출력하므로 단순히 'Python' 포함 여부로는 걸러지지 않는다.
     */
    private static final Pattern VERSION = Pattern.compile("Python \\d+\\.\\d+");

    /** 한 번 찾으면 재사용한다. 문제 생성 때마다 프로세스를 세 번씩 띄울 이유가 없다. */
    private static volatile String resolvedPython;

    private static String pythonCommand() {
        String cached = resolvedPython;
        if (cached != null) {
            return cached;
        }
        synchronized (PythonRunner.class) {
            if (resolvedPython != null) {
                return resolvedPython;
            }
            for (String candidate : PYTHON_CANDIDATES) {
                try {
                    ProcessUtils.Result r = ProcessUtils.run(
                            List.of(candidate, "--version"), null, PROBE_TIMEOUT);
                    if (!r.timedOut() && r.exit() == 0
                            && VERSION.matcher(r.stdout() + r.stderr()).find()) {
                        resolvedPython = candidate;
                        return candidate;
                    }
                } catch (Exception ignored) {
                    // 그 이름이 없는 것뿐이므로 다음 후보로 넘어간다
                }
            }
            // 못 찾아도 실행은 시도한다. 실패 사유가 로그에 남아야 원인을 알 수 있다.
            resolvedPython = PYTHON_CANDIDATES.get(0);
            return resolvedPython;
        }
    }

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
                    List.of(pythonCommand(), script.toString()), tmp.toFile(), EXEC_TIMEOUT);

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
