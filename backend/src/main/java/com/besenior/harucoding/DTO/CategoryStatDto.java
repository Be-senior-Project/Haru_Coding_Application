package com.besenior.harucoding.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatDto {
    private int topicId;
    private String topicName;
    private double correctRate;  // 0.0 ~ 1.0
    private int correctCount;
    private int totalSolved;
}
