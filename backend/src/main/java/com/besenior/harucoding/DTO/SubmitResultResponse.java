package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class SubmitResultResponse {
    private int totalProblems;
    private int correctCount;
    private int xpEarned;
    private boolean streakUpdated;
    private int currentStreak;
    private List<ProblemResult> results;

    @Getter
    @Builder
    public static class ProblemResult {
        private Long problemId;
        private boolean isCorrect;
        private Object correctAnswer;
        private String explanation;
    }
}