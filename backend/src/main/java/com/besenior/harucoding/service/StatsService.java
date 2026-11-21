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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final TopicRepository topicRepository;

    /** 약점 기준: 정답률이 이 값(%) 미만이면서 이 수 이상 푼 영역. 학습 통계의 약점 안내와 약점 기반 문제 생성이 같이 쓴다. */
    public static final double WEAK_ACCURACY_BELOW = 70.0;
    public static final int WEAK_MIN_SOLVED = 3;

    @Transactional(readOnly = true)
    public StatsResponse getStats(Long userId, String period) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime since = periodStart(period);

        List<UserProblemRecord> allRecords = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);
        // 통계는 문제별 첫 제출 기록으로 센다. 같은 문제를 다시 제출해도 푼 문제 수·정답률이 부풀지 않게 하기 위함.
        // 기간 탭은 그 문제를 처음 푼 날짜로 나눈다.
        List<UserProblemRecord> firstAttempts = firstAttempts(allRecords);
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
                .recentRecords(recentRecords)
                .build();
    }

    /**
     * 약점 영역: 기준(WEAK_MIN_SOLVED문제 이상 풀고 정답률 WEAK_ACCURACY_BELOW% 미만)을 만족하는 영역 중
     * 정답률이 가장 낮은 영역의 분류명. 없으면 null. (영역이 하나뿐이라고 약점으로 보지 않는다)
     */
    static String weakCategory(List<StatsResponse.CategoryStat> stats) {
        return stats.stream()
                .filter(c -> c.getTotalSolved() >= WEAK_MIN_SOLVED && c.getAccuracyRate() < WEAK_ACCURACY_BELOW)
                .min(Comparator.comparingDouble(StatsResponse.CategoryStat::getAccuracyRate))
                .map(StatsResponse.CategoryStat::getTopicName)
                .orElse(null);
    }

    /** 문제별 첫 제출 기록만 남긴다(입력·출력 모두 최신순). */
    static List<UserProblemRecord> firstAttempts(List<UserProblemRecord> newestFirst) {
        Map<Long, UserProblemRecord> first = new LinkedHashMap<>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            UserProblemRecord r = newestFirst.get(i);
            first.putIfAbsent(r.getProblem().getId(), r);
        }
        List<UserProblemRecord> result = new ArrayList<>(first.values());
        result.sort(Comparator.comparing(UserProblemRecord::getSolvedAt).reversed());
        return result;
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
