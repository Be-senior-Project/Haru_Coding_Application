package com.besenior.harucoding.generation.verify;

/**
 * 코드 검증 결과 표준 포맷.
 * reason: compile_error | runtime_error | timeout | output_mismatch | internal_error
 */
public record VerifyResult(boolean ok, String reason, String detail) {

    public static VerifyResult pass() {
        return new VerifyResult(true, null, null);
    }

    public static VerifyResult fail(String reason, String detail) {
        return new VerifyResult(false, reason, detail);
    }
}
