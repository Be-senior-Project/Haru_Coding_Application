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
}
