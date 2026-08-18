package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.ProblemScrap;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 스크랩 목록 1건. 문제은행 탭 > 스크랩에 노출된다. 필드 구성은 WrongNoteResponse와 맞춰 두었다. */
@Getter
@Builder
public class ScrapResponse {

    private Long scrapId;
    private Long problemId;
    private String title;
    private String category;
    private String subcategory;
    private int difficulty;      // 0=입문, 1=초급, 2=중급
    private String language;     // Python, Java, C++
    private String type;         // IMPLEMENTATION, DEBUGGING, FILL_IN_THE_BLANK
    private LocalDateTime scrappedAt;

    public static ScrapResponse from(ProblemScrap scrap) {
        Problem problem = scrap.getProblem();
        return ScrapResponse.builder()
                .scrapId(scrap.getId())
                .problemId(problem.getId())
                .title(problem.getTitle())
                .category(problem.getCategory())
                .subcategory(problem.getSubcategory())
                .difficulty(problem.getDifficulty())
                .language(problem.getLanguage())
                .type(problem.getType().name())
                .scrappedAt(scrap.getCreatedAt())
                .build();
    }
}
