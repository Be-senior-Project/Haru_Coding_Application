package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;

/** 개별 문제 채점 결과. */
@Getter
@Builder
public class AttemptResultResponse {
    private Long problemId;
    private boolean correct;
    private Object correctAnswer;   // 제출 후엔 정답 공개
    private String explanation;
    private int xpEarned;
    private int currentStreak;
    // 통과한 테스트 수 / 전체 테스트 수(예시 + 숨겨진 테스트). 숨겨진 테스트의 내용은 보내지 않는다.
    private int testsPassed;
    private int testsTotal;

    // 오답 이유 (코드를 실행해 채점했을 때만). 숨겨진 테스트의 입력·출력과, 숨겨진 테스트에서 난 오류 메시지는 담지 않는다.
    private String failReason;      // compile_error | runtime_error | timeout | output_mismatch
    private Integer failedTest;     // 처음 틀린 테스트: 0 = 예시, n = 숨겨진 테스트 n번, null = 알 수 없음
    private String failMessage;     // 컴파일·실행 오류 메시지 등
    private String expectedOutput;  // 예시에서 틀렸을 때 기대 출력
    private String actualOutput;    // 예시에서 틀렸을 때 내 출력
}
