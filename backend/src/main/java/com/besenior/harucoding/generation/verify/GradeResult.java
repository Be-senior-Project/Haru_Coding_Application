package com.besenior.harucoding.generation.verify;

/**
 * 예시 + 숨겨진 테스트로 채점한 결과.
 * passed/total: 통과한 테스트 수 / 전체 테스트 수(예시 포함).
 * reason: 실패 사유(output_mismatch | compile_error | runtime_error | timeout | internal_error), 통과면 null.
 * detail에는 숨겨진 테스트의 입력·기대 출력을 넣지 않는다(정답 유출 방지).
 */
public record GradeResult(boolean ok, int passed, int total, String reason, String detail) {

    static GradeResult internalError(String detail) {
        return new GradeResult(false, 0, 0, "internal_error", detail);
    }
}
