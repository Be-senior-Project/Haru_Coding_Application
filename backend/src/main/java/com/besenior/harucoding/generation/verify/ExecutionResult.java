package com.besenior.harucoding.generation.verify;

import java.util.List;

/**
 * 여러 입력을 한 번의 컴파일·실행으로 처리한 결과.
 * ok면 outputs.get(i)가 i번째 입력에 대한 solution 반환값(JSON 한 줄)이다.
 * 실패면 reason(compile_error | runtime_error | timeout | internal_error)과 detail에 사유가 있고,
 * outputs에는 실패 직전까지 출력된 줄이 들어 있다.
 */
public record ExecutionResult(boolean ok, String reason, String detail, List<String> outputs) {

    public static ExecutionResult success(List<String> outputs) {
        return new ExecutionResult(true, null, null, List.copyOf(outputs));
    }

    public static ExecutionResult fail(String reason, String detail) {
        return new ExecutionResult(false, reason, detail, List.of());
    }

    public static ExecutionResult fail(String reason, String detail, List<String> partialOutputs) {
        return new ExecutionResult(false, reason, detail, List.copyOf(partialOutputs));
    }
}
