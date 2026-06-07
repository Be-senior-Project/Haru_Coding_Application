package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.StatsResponse;
import com.besenior.harucoding.entity.UserCategoryStat;
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
    private final UserCategoryStatRepository categoryStatRepository;
    private final UserStreakLogRepository streakLogRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        long totalSolved  = recordRepository.countTotalByUserId(userId);
        long correctCount = recordRepository.countCorrectByUserId(userId);
        double accuracy   = totalSolved == 0 ? 0.0
                : Math.round((double) correctCount / totalSolved * 1000) / 10.0;

        // 현재 스트릭
        int currentStreak = streakLogRepository.findLatestByUserId(userId)
                .map(UserStreakLog::getStreakCount)
                .orElse(0);

        // 주간 활동 (이번 주 월~일)
        List<Integer> weeklyActivity = getWeeklyActivity(userId);

        // 카테고리별 통계
        List<StatsResponse.CategoryStat> categoryStats = categoryStatRepository
                .findByUserId(userId).stream()
                .map(stat -> StatsResponse.CategoryStat.builder()
                        .topicId(stat.getTopic().getId())
                        .topicName(stat.getTopic().getName())
                        .icon(stat.getTopic().getIcon())
                        .totalSolved(stat.getTotalSolved())
                        .correctCount(stat.getCorrectCount())
                        .accuracyRate(stat.getAccuracyRate())
                        .build())
                .collect(Collectors.toList());

        // 최근 풀이 5개
        List<StatsResponse.RecentRecord> recentRecords = recordRepository
                .findByUserIdOrderBySolvedAtDesc(userId).stream()
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