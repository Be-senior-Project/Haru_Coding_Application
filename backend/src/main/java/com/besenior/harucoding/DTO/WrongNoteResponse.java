package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.UserProblemRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 오답노트 목록 1건. 문제은행 탭 > 오답노트에 노출된다. */
@Getter
@Builder
public class WrongNoteResponse {

    private Long recordId;
    private Long problemId;
    private String title;
    private String category;
    private String subcategory;
    private int difficulty;      // 0=입문, 1=초급, 2=중급
    private String language;     // Python, Java, C++
    private String type;         // IMPLEMENTATION, DEBUGGING, FILL_IN_THE_BLANK
    private Object userAnswer;   // 마지막으로 제출했던 오답
    private Integer timeSpentSec;
    private LocalDateTime solvedAt;

    public static WrongNoteResponse from(UserProblemRecord record) {
        Problem problem = record.getProblem();
        return WrongNoteResponse.builder()
                .recordId(record.getId())
                .problemId(problem.getId())
                .title(problem.getTitle())
                .category(problem.getCategory())
                .subcategory(problem.getSubcategory())
                .difficulty(problem.getDifficulty())
                .language(problem.getLanguage())
                .type(problem.getType().name())
                .userAnswer(record.getUserAnswer())
                .timeSpentSec(record.getTimeSpentSec())
                .solvedAt(record.getSolvedAt())
                .build();
    }
}
