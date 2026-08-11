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
}
