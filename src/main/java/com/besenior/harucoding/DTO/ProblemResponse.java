package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.Problem;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ProblemResponse {
    private Long id;
    private String setId;
    private String type;
    private String category;
    private String subcategory;
    private int difficulty;
    private String language;
    private String title;
    private String description;
    private List<String> constraints;
    private Map<String, String> ioExample;
    private String codeSkeleton;
    // 제출 전엔 null로 내려줌 (정답 노출 방지)
    private Object answer;
    private String explanation;
    private String conceptExplanation;

    public static ProblemResponse from(Problem p, boolean includeAnswer) {
        return ProblemResponse.builder()
                .id(p.getId())
                .setId(p.getSetId())
                .type(p.getType().name())
                .category(p.getCategory())
                .subcategory(p.getSubcategory())
                .difficulty(p.getDifficulty())
                .language(p.getLanguage())
                .title(p.getTitle())
                .description(p.getDescription())
                .constraints(p.getConstraints())
                .ioExample(p.getIoExample())
                .codeSkeleton(p.getCodeSkeleton())
                .answer(includeAnswer ? p.getAnswer() : null)
                .explanation(includeAnswer ? p.getExplanation() : null)
                .conceptExplanation(p.getConceptExplanation())
                .build();
    }
}
