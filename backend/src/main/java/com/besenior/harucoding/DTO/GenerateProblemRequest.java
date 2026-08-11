package com.besenior.harucoding.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 문제 생성 요청. (필수: category, difficulty, language) */
@Getter
@Setter
@NoArgsConstructor
public class GenerateProblemRequest {

    private String category;          // Basic/Introductory | Algorithm/Data Structure | Past Exam Prep
    private String subcategory;       // nullable (Basic는 없음)
    private Integer difficulty;       // 0 | 1 | 2
    private String language;          // Python | Java | C++
    private List<String> types;       // Implementation | Debugging | Fill-in-the-blank (기본: 전체)
    private Integer problemsPerSet;   // 기본 3
}
