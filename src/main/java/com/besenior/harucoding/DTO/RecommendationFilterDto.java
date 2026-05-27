package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendationFilterDto {
    private String difficulty;
    private List<Integer> topicIds;
    private String type;
    private String style;
    private String language;
    private String reason;
    private String focusPoint;
    private String method;
}
