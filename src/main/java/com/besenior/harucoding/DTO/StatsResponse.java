package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class StatsResponse {
    private long totalSolved;
    private long correctCount;
    private double accuracyRate;
    private int currentStreak;
    private List<Integer> weeklyActivity;   // 월~일 7일치 풀이 수
    private List<CategoryStat> categoryStats;
    private List<RecentRecord> recentRecords;

    @Getter
    @Builder
    public static class CategoryStat {
        private Long topicId;
        private String topicName;
        private String icon;
        private int totalSolved;
        private int correctCount;
        private double accuracyRate;
    }

    @Getter
    @Builder
    public static class RecentRecord {
        private Long problemId;
        private String problemTitle;
        private String topic;
        private boolean isCorrect;
        private String solvedAt;
    }
}