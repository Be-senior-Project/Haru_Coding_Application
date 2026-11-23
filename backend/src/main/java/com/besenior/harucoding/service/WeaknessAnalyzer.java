package com.besenior.harucoding.service;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.UserProblemRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 약점 판단 기준을 한 곳에 모은다. 학습 통계 화면, 추천 엔진, 오늘의 세트(약점 기반 문제 생성)가 같이 쓴다.
 *
 * 기준: 문제별 첫 제출 기록으로 집계해 WEAK_MIN_SOLVED문제 이상 풀었고 정답률이 WEAK_ACCURACY_BELOW% 미만인 영역.
 * 같은 문제를 다시 제출한 기록은 세지 않는다(모범 답안을 본 뒤 다시 맞힌 기록이 정답률을 부풀리지 않게).
 * 약점 영역은 상위 분류(category)로 고르고, 그 안에서 정답률이 가장 낮은 하위 분류(subcategory)를 함께 구한다.
 */
public final class WeaknessAnalyzer {

    public static final double WEAK_ACCURACY_BELOW = 70.0;
    public static final int WEAK_MIN_SOLVED = 3;

    private WeaknessAnalyzer() {}

    /** 약점 영역. subcategory는 하위 분류가 없는 분류(기초/입문)이거나 하위 분류 기록이 없으면 null. */
    public record Weakness(String category, String subcategory) {
        /** 표시용 영역 이름: 하위 분류가 있으면 하위 분류, 없으면 분류. */
        public String area() {
            return subcategory != null ? subcategory : category;
        }
    }

    /** 영역별 집계. solved는 푼 문제 수(첫 제출 기준), correct는 그중 맞힌 수. */
    public record AreaStat(String area, int solved, int correct) {
        public double rate() {
            return solved == 0 ? 0.0 : Math.round((double) correct / solved * 1000) / 10.0;
        }
    }

    /** 문제별 첫 제출 기록만 남긴다(입력·출력 모두 최신순). */
    public static List<UserProblemRecord> firstAttempts(List<UserProblemRecord> newestFirst) {
        Map<Long, UserProblemRecord> first = new LinkedHashMap<>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            UserProblemRecord r = newestFirst.get(i);
            first.putIfAbsent(r.getProblem().getId(), r);
        }
        List<UserProblemRecord> result = new ArrayList<>(first.values());
        result.sort(Comparator.comparing(UserProblemRecord::getSolvedAt).reversed());
        return result;
    }

    /** 기록을 영역별로 집계한다. key가 null을 돌려주는 기록은 뺀다. */
    public static Map<String, AreaStat> group(List<UserProblemRecord> records, Function<Problem, String> key) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (UserProblemRecord r : records) {
            String area = key.apply(r.getProblem());
            if (area == null) continue;
            int[] c = counts.computeIfAbsent(area, k -> new int[2]);
            c[0]++;
            if (r.isCorrect()) c[1]++;
        }
        Map<String, AreaStat> result = new LinkedHashMap<>();
        counts.forEach((area, c) -> result.put(area, new AreaStat(area, c[0], c[1])));
        return result;
    }

    /** 기준을 만족하는 영역(정답률 낮은 순). */
    public static List<AreaStat> weakAreas(Collection<AreaStat> stats) {
        return stats.stream()
                .filter(s -> s.solved() >= WEAK_MIN_SOLVED && s.rate() < WEAK_ACCURACY_BELOW)
                .sorted(Comparator.comparingDouble(AreaStat::rate))
                .toList();
    }

    /** 기준을 만족하는 영역 중 정답률이 가장 낮은 영역 이름. 없으면 null. */
    public static String weakest(Collection<AreaStat> stats) {
        List<AreaStat> weak = weakAreas(stats);
        return weak.isEmpty() ? null : weak.get(0).area();
    }

    /** 사용자의 풀이 기록(최신순)으로 약점 영역을 구한다. 기준을 만족하는 분류가 없으면 null. */
    public static Weakness find(List<UserProblemRecord> newestFirst) {
        List<UserProblemRecord> first = firstAttempts(newestFirst);
        String category = weakest(group(first, Problem::getCategory).values());
        if (category == null) return null;

        // 약점 분류 안에서 정답률이 가장 낮은 하위 분류(같으면 더 많이 푼 쪽)
        List<UserProblemRecord> inCategory = first.stream()
                .filter(r -> category.equals(r.getProblem().getCategory()))
                .toList();
        String subcategory = group(inCategory, Problem::getSubcategory).values().stream()
                .min(Comparator.comparingDouble(AreaStat::rate)
                        .thenComparing(Comparator.comparingInt(AreaStat::solved).reversed()))
                .map(AreaStat::area)
                .orElse(null);
        return new Weakness(category, subcategory);
    }
}
