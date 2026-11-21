package com.besenior.harucoding.generation.verify;

/**
 * 예시 + 숨겨진 테스트로 채점한 결과.
 * passed/total: 통과한 테스트 수 / 전체 테스트 수(예시 포함).
 * reason: 실패 사유(output_mismatch | compile_error | runtime_error | timeout | internal_error), 통과면 null.
 * detail: 서버 기록용 설명. 숨겨진 테스트의 입력·기대 출력은 넣지 않는다(정답 유출 방지).
 *
 * 아래는 앱에 보여 줄 오답 이유다. 숨겨진 테스트의 내용이 드러나지 않는 값만 채운다.
 * failedTest: 처음 틀린 테스트 번호(0 = 예시, n = 숨겨진 테스트 n번). 컴파일 오류·시간 초과처럼 알 수 없으면 null.
 * message: 컴파일·실행 오류 메시지 등. 숨겨진 테스트를 실행하다 난 오류의 메시지는 입력값이 드러날 수 있어 넣지 않는다.
 * expectedOutput/actualOutput: 예시(공개된 값)에서 출력이 다를 때의 기대 출력과 사용자 출력.
 */
public record GradeResult(boolean ok, int passed, int total, String reason, String detail,
                          Integer failedTest, String message, String expectedOutput, String actualOutput) {

    public GradeResult(boolean ok, int passed, int total, String reason, String detail) {
        this(ok, passed, total, reason, detail, null, null, null, null);
    }

    static GradeResult internalError(String detail) {
        return new GradeResult(false, 0, 0, "internal_error", detail);
    }
}
