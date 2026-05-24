package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.ProblemSet;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ProblemSetResponse {
    private Long id;
    private String title;
    private String targetDate;
    private String difficulty;
    private boolean isAiGenerated;
    private List<ProblemResponse> problems;

    public static ProblemSetResponse from(ProblemSet set, boolean includeAnswer) {
        return ProblemSetResponse.builder()
                .id(set.getId())
                .title(set.getTitle())
                .targetDate(set.getTargetDate().toString())
                .difficulty(set.getDifficulty().name())
                .isAiGenerated(set.isAiGenerated())
                .problems(set.getItems().stream()
                        .map(item -> ProblemResponse.from(item.getProblem(), includeAnswer))
                        .collect(Collectors.toList()))
                .build();
    }
}