package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.StatsResponse;
import com.besenior.harucoding.entity.Topic;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.entity.UserStreakLog;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats(Long userId, String period) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime since = periodStart(period);

        List<UserProblemRecord> allRecords = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);
        // 통계는 문제별 첫 제출 기록으로 센다. 같은 문제를 다시 제출해도 푼 문제 수·정답률이 부풀지 않게 하기 위함.
        // 기간 탭은 그 문제를 처음 푼 날짜로 나눈다.
        List<UserProblemRecord> firstAttempts = WeaknessAnalyzer.firstAttempts(allRecords);
        List<UserProblemRecord> records = inPeriod(firstAttempts, since);

        long totalSolved  = records.size();
        long correctCount = records.stream().filter(UserProblemRecord::isCorrect).count();
        double accuracy   = rate(correctCount, totalSolved);

        // 현재 스트릭 (기간과 무관하게 연속 학습일은 항상 전체 기준)
        int currentStreak = getCurrentStreak(userId);

        // 이번 주(월~일) 날짜별 처음 푼 문제 수. 탭과 무관하게 같은 그래프
        List<Integer> weeklyActivity = getWeeklyActivity(firstAttempts);

        // 카테고리별 통계 (선택된 기간에 처음 푼 문제로 재집계)
        List<StatsResponse.CategoryStat> categoryStats = buildCategoryStats(records);

        // 최근 풀이 5개 (선택된 기간 내 실제 제출. 다시 푼 것도 포함)
        List<StatsResponse.RecentRecord> recentRecords = inPeriod(allRecords, since).stream()
                .limit(5)
                .map(r -> StatsResponse.RecentRecord.builder()
                        .problemId(r.getProblem().getId())
                        .problemTitle(r.getProblem().getTitle())
                        .topic(r.getProblem().getCategory())
                        .isCorrect(r.isCorrect())
                        .solvedAt(r.getSolvedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build())
                .collect(Collectors.toList());

        return StatsResponse.builder()
                .totalSolved(totalSolved)
                .correctCount(correctCount)
                .accuracyRate(accuracy)
                .currentStreak(currentStreak)
                .weeklyActivity(weeklyActivity)
                .categoryStats(categoryStats)
                .weakCategory(weakCategory(categoryStats))
                .dailyActivity(since == null ? Map.of() : dailyActivity(records))
                .typeStats(breakdown(records, r -> r.getProblem().getType().name(), TYPE_KEYS))
                .difficultyStats(breakdown(records, r -> String.valueOf(r.getProblem().getDifficulty()), DIFFICULTY_KEYS))
                .recentRecords(recentRecords)
                .build();
    }

    private static final List<String> TYPE_KEYS = List.of("IMPLEMENTATION", "DEBUGGING", "FILL_IN_THE_BLANK");
    private static final List<String> DIFFICULTY_KEYS = List.of("0", "1", "2");

    /** keys 순서대로(기록이 없는 키도 0으로) 정답률을 집계한다. 전체 탭의 유형별·난이도별 카드용. */
    private static List<StatsResponse.BreakdownStat> breakdown(List<UserProblemRecord> records,
                                                               Function<UserProblemRecord, String> key,
                                                               List<String> keys) {
        Map<String, int[]> counts = new HashMap<>();
        for (UserProblemRecord r : records) {
            int[] c = counts.computeIfAbsent(key.apply(r), k -> new int[2]);
            c[0]++;
            if (r.isCorrect()) c[1]++;
        }
        return keys.stream().map(k -> {
            int[] c = counts.getOrDefault(k, new int[2]);
            return StatsResponse.BreakdownStat.builder()
                    .key(k).totalSolved(c[0]).correctCount(c[1]).accuracyRate(rate(c[1], c[0]))
                    .build();
        }).toList();
    }

    /** 날짜별(KST) 풀이 수. 푼 날만 담는다. 월간 달력·연간 히트맵용. */
    private static Map<String, Integer> dailyActivity(List<UserProblemRecord> records) {
        Map<String, Integer> daily = new TreeMap<>();
        for (UserProblemRecord r : records) {
            daily.merge(r.getSolvedAt().atZone(KST).toLocalDate().toString(), 1, Integer::sum);
        }
        return daily;
    }

    /**
     * 약점 영역(공용 기준 WeaknessAnalyzer: 3문제 이상 풀고 첫 제출 정답률 70% 미만인 영역 중 가장 낮은 곳).
     * 없으면 null. 영역이 하나뿐이라고 약점으로 보지 않는다.
     */
    static String weakCategory(List<StatsResponse.CategoryStat> stats) {
        return WeaknessAnalyzer.weakest(stats.stream()
                .map(c -> new WeaknessAnalyzer.AreaStat(c.getTopicName(), c.getTotalSolved(), c.getCorrectCount()))
                .toList());
    }

    private static List<UserProblemRecord> inPeriod(List<UserProblemRecord> records, LocalDateTime since) {
        return since == null ? records
                : records.stream().filter(r -> !r.getSolvedAt().isBefore(since)).toList();
    }

    private static double rate(long correct, long total) {
        return total == 0 ? 0.0 : Math.round((double) correct / total * 1000) / 10.0;
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** period(ALL|WEEK|MONTH|YEAR) → 집계 시작 시각. ALL 또는 알 수 없는 값이면 null(제한 없음). */
    private LocalDateTime periodStart(String period) {
        LocalDate today = LocalDate.now(KST);
        return switch (period == null ? "ALL" : period.toUpperCase()) {
            case "WEEK" -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "MONTH" -> today.withDayOfMonth(1).atStartOfDay();
            case "YEAR" -> today.withDayOfYear(1).atStartOfDay();
            default -> null;
        };
    }

    /** 주어진 풀이 기록(이미 기간 필터링됨)을 problem.category 기준으로 재집계. */
    private List<StatsResponse.CategoryStat> buildCategoryStats(List<UserProblemRecord> records) {
        Map<String, List<UserProblemRecord>> byCategory = records.stream()
                .collect(Collectors.groupingBy(r -> r.getProblem().getCategory()));

        List<StatsResponse.CategoryStat> result = new ArrayList<>();
        for (var entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<UserProblemRecord> group = entry.getValue();
            int total = group.size();
            int correct = (int) group.stream().filter(UserProblemRecord::isCorrect).count();

            Topic topic = topicRepository.findByName(category).orElse(null);
            result.add(StatsResponse.CategoryStat.builder()
                    .topicId(topic != null ? topic.getId() : null)
                    .topicName(category)
                    .icon(topic != null ? topic.getIcon() : null)
                    .totalSolved(total)
                    .correctCount(correct)
                    .accuracyRate(rate(correct, total))
                    .build());
        }
        return result;
    }

    /** 마지막 학습일이 오늘 또는 어제가 아니면 streak는 0 */
    private int getCurrentStreak(Long userId) {
        return streakLogRepository.findLatestByUserId(userId)
                .filter(log -> {
                    LocalDate last = log.getStreakDate();
                    LocalDate today = LocalDate.now(KST);
                    return last.equals(today) || last.equals(today.minusDays(1));
                })
                .map(UserStreakLog::getStreakCount)
                .orElse(0);
    }

    /** 이번 주(월~일) 날짜별 풀이 수. */
    private List<Integer> getWeeklyActivity(List<UserProblemRecord> records) {
        LocalDate monday = LocalDate.now(KST).with(DayOfWeek.MONDAY);

        Map<LocalDate, Long> countByDate = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSolvedAt().atZone(KST).toLocalDate(),
                        Collectors.counting()
                ));

        List<Integer> weekly = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekly.add(countByDate.getOrDefault(monday.plusDays(i), 0L).intValue());
        }
        return weekly;
    }
}
