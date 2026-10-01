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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    @Transactional(readOnly = true)
    public StatsResponse getStats(Long userId, String period) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime since = periodStart(period);

        List<UserProblemRecord> allRecords = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);
        List<UserProblemRecord> records = since == null ? allRecords
                : allRecords.stream().filter(r -> !r.getSolvedAt().isBefore(since)).toList();

        long totalSolved  = records.size();
        long correctCount = records.stream().filter(UserProblemRecord::isCorrect).count();
        double accuracy   = totalSolved == 0 ? 0.0
                : Math.round((double) correctCount / totalSolved * 1000) / 10.0;

        // 현재 스트릭 (기간과 무관하게 연속 학습일은 항상 전체 기준)
        int currentStreak = streakLogRepository.findLatestByUserId(userId)
                .map(UserStreakLog::getStreakCount)
                .orElse(0);

        // 주간 활동 (이번 주 월~일) — 탭과 무관하게 항상 최근 7일 위젯
        List<Integer> weeklyActivity = getWeeklyActivity(userId);

        // 카테고리별 통계 (선택된 기간의 풀이 기록으로 재집계)
        List<StatsResponse.CategoryStat> categoryStats = buildCategoryStats(records);

        // 최근 풀이 5개 (선택된 기간 내)
        List<StatsResponse.RecentRecord> recentRecords = records.stream()
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
                .recentRecords(recentRecords)
                .build();
    }

    /** period(ALL|WEEK|MONTH|YEAR) → 집계 시작 시각. ALL 또는 알 수 없는 값이면 null(제한 없음). */
    private LocalDateTime periodStart(String period) {
        LocalDate today = LocalDate.now();
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
            double rate = total == 0 ? 0.0 : Math.round((double) correct / total * 1000) / 10.0;

            Topic topic = topicRepository.findByName(category).orElse(null);
            result.add(StatsResponse.CategoryStat.builder()
                    .topicId(topic != null ? topic.getId() : null)
                    .topicName(category)
                    .icon(topic != null ? topic.getIcon() : null)
                    .totalSolved(total)
                    .correctCount(correct)
                    .accuracyRate(rate)
                    .build());
        }
        return result;
    }

    private List<Integer> getWeeklyActivity(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);

        List<UserProblemRecord> records = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);

        // 날짜별 풀이 수 집계
        Map<LocalDate, Long> countByDate = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getSolvedAt().toLocalDate(),
                        Collectors.counting()
                ));

        List<Integer> weekly = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            weekly.add(countByDate.getOrDefault(date, 0L).intValue());
        }
        return weekly;
    }
}