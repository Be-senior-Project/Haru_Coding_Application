package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 사용자 역량 기반 추천 응답 (프론트 출력용).
 *
 * - summary         : 대시보드용 역량 요약 (정답률, 추정 레벨, 강/약점 영역)
 * - recommendations : 다음에 풀 문제 목록 + 추천 근거
 */
@Getter
@Builder
public class RecommendationResponse {

    private Long userId;
    private String generatedAt;                       // ISO-8601
    private String strategy;                          // COLD_START | PERSONALIZED | SIMILARITY
    private CompetencySummary summary;
    private List<RecommendedProblem> recommendations;

    /** 역량 요약 */
    @Getter
    @Builder
    public static class CompetencySummary {
        private long totalSolved;          // 누적 풀이 수(시도 기준)
        private double accuracyRate;        // 전체 정답률 (0~100)
        private int estimatedLevel;        // 추정 난이도 레벨 (0/1/2)
        private List<AreaStat> weakAreas;  // 취약 영역 (정답률 낮은 순)
        private List<AreaStat> strongAreas;// 강점 영역 (정답률 높은 순)
    }

    /** 영역(subcategory, 없으면 category)별 통계 */
    @Getter
    @Builder
    public static class AreaStat {
        private String area;        // subcategory(없으면 category)
        private int attempts;       // 시도 수
        private int correct;        // 정답 수
        private double accuracyRate; // 0~100
    }

    /** 추천 문제 1건 */
    @Getter
    @Builder
    public static class RecommendedProblem {
        private Long problemId;
        private String title;
        private String category;
        private String subcategory;
        private int difficulty;          // 0/1/2
        private String type;             // IMPLEMENTATION | DEBUGGING | FILL_IN_THE_BLANK
        private String language;
        private double similarityScore;  // 약점 벡터와의 코사인 유사도 (0~1), 구조적 폴백이면 0
        private String matchedWeakness;  // 이 문제가 보완하는 약점 영역 (nullable)
        private boolean review;          // 이전 오답 재출제(복습) 여부
        private String reason;           // 사람이 읽는 추천 근거
    }
}
