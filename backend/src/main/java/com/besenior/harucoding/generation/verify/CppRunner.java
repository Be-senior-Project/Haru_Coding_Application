package com.besenior.harucoding.generation.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * C++ 실행기. resources/runner/cpp_template.txt 를 로드해 조립 후 g++ 컴파일/실행.
 * 반환값은 __json 오버로드로 JSON 직렬화해 출력 → Python json.dumps 형식과 일치시킨다.
 */
public class CppRunner implements Runner {

    private static final int COMPILE_TIMEOUT = 20;
    private static final int EXEC_TIMEOUT = 5;
    // 주소 공간 상한(격리 실행 시에만 적용). 컴파일러는 bits/stdc++.h 처리에 메모리를 많이 써서 더 크게 둔다.
    private static final long COMPILE_MEMORY_LIMIT = 1536L * 1024 * 1024;
    private static final long EXEC_MEMORY_LIMIT = 512L * 1024 * 1024;

    /**
     * 입력마다 main 안에 블록을 하나씩 만든다. 블록이 달라야 입력마다 같은 변수 이름을 다시 선언할 수 있다.
     * 사용자 코드는 마지막에 넣는다(RunnerSupport.replaceOnce 참고).
     */
    static String buildProgram(String template, String answerCode, List<String> ioInputs, String args) {
        StringBuilder cases = new StringBuilder();
        for (String io : ioInputs) {
            cases.append("    {\n")
                    .append("        ").append(RunnerSupport.indentBody(io, 8)).append('\n')
                    .append("        cout << __json(solution(").append(args).append(")) << endl;\n")
                    .append("    }\n");
        }
        String withCases = RunnerSupport.replaceOnce(template, "__CASES__", cases.toString());
        return RunnerSupport.replaceOnce(withCases, "__ANSWER__", answerCode);
    }

    @Override
    public ExecutionResult execute(String answerCode, List<String> ioInputs, String signature) {
        List<String> params;
        try {
            params = SignatureParser.extractParamNames(signature);
        } catch (Exception e) {
            return ExecutionResult.fail("internal_error", "Signature 파싱 실패: " + e.getMessage());
        }

        Path tmp = null;
        try {
            String program = buildProgram(RunnerSupport.loadTemplate("runner/cpp_template.txt"),
                    answerCode, ioInputs, String.join(", ", params));
            tmp = Files.createTempDirectory("verify_cpp");
            Path src = tmp.resolve("solution.cpp");
            Path bin = tmp.resolve("solution");
            Files.writeString(src, program);

            ProcessUtils.Result compile = ProcessUtils.run(
                    List.of("g++", "-std=c++17", "-O2", "-o", bin.toString(), src.toString()),
                    tmp.toFile(), COMPILE_TIMEOUT, COMPILE_MEMORY_LIMIT);
            if (compile.timedOut()) {
                return ExecutionResult.fail("compile_error", "컴파일 시간 " + COMPILE_TIMEOUT + "초 초과");
            }
            if (compile.exit() != 0) {
                return ExecutionResult.fail("compile_error", RunnerSupport.blankToDefault(compile.stderr(), "g++ failed"));
            }

            ProcessUtils.Result exec = ProcessUtils.run(
                    List.of(bin.toString()), tmp.toFile(), EXEC_TIMEOUT, EXEC_MEMORY_LIMIT);
            if (exec.timedOut()) {
                return ExecutionResult.fail("timeout", "실행 시간 " + EXEC_TIMEOUT + "초 초과");
            }
            if (exec.exit() != 0) {
                return ExecutionResult.fail("runtime_error",
                        RunnerSupport.blankToDefault(exec.stderr(), "non-zero exit code"),
                        RunnerSupport.outputLines(exec.stdout()));
            }
            return ExecutionResult.success(RunnerSupport.outputLines(exec.stdout()));
        } catch (Exception e) {
            return ExecutionResult.fail("internal_error", "실행 오류: " + e.getMessage());
        } finally {
            RunnerSupport.deleteQuietly(tmp);
        }
    }
}
