package com.besenior.harucoding.DTO;

import lombok.Getter;
import java.util.List;

@Getter
public class SubmitAnswerRequest {
    private List<AnswerItem> answers;

    @Getter
    public static class AnswerItem {
        private Long problemId;
        private Object answer;
        private Integer timeSpentSec;
    }
}