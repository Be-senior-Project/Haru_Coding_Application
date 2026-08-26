package com.besenior.harucoding.generation.verify;

/**
 * 코드 검증 결과 표준 포맷.
 * reason: compile_error | runtime_error | timeout | output_mismatch | internal_error
 * actualOutput: 실제 stdout (컴파일 실패·타임아웃 등 실행 자체가 안 된 경우 null) — "코드 실행"(SOLVE-007) 결과 표시용.
 */
public record VerifyResult(boolean ok, String reason, String detail, String actualOutput) {

    public static VerifyResult pass(String actualOutput) {
        return new VerifyResult(true, null, null, actualOutput);
    }

    public static VerifyResult fail(String reason, String detail) {
        return new VerifyResult(false, reason, detail, null);
    }

    public static VerifyResult fail(String reason, String detail, String actualOutput) {
        return new VerifyResult(false, reason, detail, actualOutput);
    }
}
