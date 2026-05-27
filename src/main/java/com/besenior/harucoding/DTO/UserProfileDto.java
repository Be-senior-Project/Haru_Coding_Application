package com.besenior.harucoding.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String codingLevel;       // "LOTS", "SOME", "NONE"
    private boolean cotePrepared;
    private String preferredLanguage;
    private int level;
    private int totalSolved;
    private int correctCount;
    private Double avgTimeSpentSec;
    private List<CategoryStatDto> categoryStats;
}
