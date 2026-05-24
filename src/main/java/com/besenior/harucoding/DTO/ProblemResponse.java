package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.Problem;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ProblemResponse {
    private Long id;
    private String type;
    private String difficulty;
    private String topic;
    private String title;
    private String question;
    private String code;
    private List<String> options;
    private List<String> blanks;
    private List<String> matchLeft;
    private List<String> matchRight;
    // 제출 전엔 null로 내려줌 (정답 노출 방지)
    private Object answer;
    private String explanation;

    public static ProblemResponse from(Problem p, boolean includeAnswer) {
        return ProblemResponse.builder()
                .id(p.getId())
                .type(p.getType().name())
                .difficulty(p.getDifficulty().name())
                .topic(p.getTopic().getName())
                .title(p.getTitle())
                .question(p.getQuestion())
                .code(p.getCode())
                .options(p.getOptions())
                .blanks(p.getBlanks())
                .matchLeft(p.getMatchLeft())
                .matchRight(p.getMatchRight())
                .answer(includeAnswer ? p.getAnswer() : null)
                .explanation(includeAnswer ? p.getExplanation() : null)
                .build();
    }
}