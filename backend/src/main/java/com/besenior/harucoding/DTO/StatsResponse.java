package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class StatsResponse {
    // 푼 문제 수·정답률은 문제별 첫 제출 기준(같은 문제를 다시 제출해도 늘지 않음)
    private long totalSolved;
    private long correctCount;
    private double accuracyRate;
    private int currentStreak;
    private List<Integer> weeklyActivity;   // 이번 주 월~일, 날짜별 처음 푼 문제 수
    private List<CategoryStat> categoryStats;
    // 약점 영역의 분류명. 3문제 이상 풀고 정답률 70% 미만인 영역 중 가장 낮은 곳, 없으면 null
    private String weakCategory;
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