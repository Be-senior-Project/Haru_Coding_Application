package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.Map;

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
    // 선택 기간(주간·월간·연간)의 날짜별 처음 푼 문제 수 (yyyy-MM-dd → 개수, 푼 날만). 월간 달력·연간 히트맵용.
    // 전체 기간이면 비어 있다.
    private Map<String, Integer> dailyActivity;
    // 선택 기간의 유형별(IMPLEMENTATION·DEBUGGING·FILL_IN_THE_BLANK)·난이도별(0·1·2) 정답률. 문제별 첫 제출 기준
    private List<BreakdownStat> typeStats;
    private List<BreakdownStat> difficultyStats;
    private List<RecentRecord> recentRecords;

    @Getter
    @Builder
    public static class BreakdownStat {
        private String key;          // 유형 이름 또는 난이도(0·1·2)
        private int totalSolved;
        private int correctCount;
        private double accuracyRate;
    }

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