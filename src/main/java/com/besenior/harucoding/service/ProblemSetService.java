package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.ProblemSetResponse;
import com.besenior.harucoding.DTO.SubmitAnswerRequest;
import com.besenior.harucoding.DTO.SubmitResultResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.global.enums.XpReason;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemSetService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final UserCategoryStatRepository categoryStatRepository;
    private final UserXpLogRepository xpLogRepository;
    private final TopicRepository topicRepository;

    private static final int XP_PER_CORRECT = 10;
    private static final int XP_STREAK_BONUS = 5;

    @Transactional(readOnly = true)
    public ProblemSetResponse getTodaySet() {
        ProblemSet set = problemSetRepository
                .findByTargetDateWithItems(LocalDate.now())
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_SET_NOT_FOUND));
        return ProblemSetResponse.from(set, false); // 정답 미포함
    }

    @Transactional
    public SubmitResultResponse submit(Long userId, Long setId, SubmitAnswerRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ProblemSet set = problemSetRepository.findByIdWithItems(setId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_SET_NOT_FOUND));

        // 문제 Map으로 변환 (id → Problem)
        Map<Long, Problem> problemMap = set.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProblem().getId(), ProblemSetItem::getProblem));

        List<SubmitResultResponse.ProblemResult> results = new ArrayList<>();
        int correctCount = 0;
        int xpEarned = 0;

        for (SubmitAnswerRequest.AnswerItem item : request.getAnswers()) {
            Problem problem = problemMap.get(item.getProblemId());
            if (problem == null) continue;

            boolean isCorrect = checkAnswer(problem, item.getAnswer());
            if (isCorrect) {
                correctCount++;
                xpEarned += XP_PER_CORRECT;
            }

            // 풀이 기록 저장
            recordRepository.save(UserProblemRecord.builder()
                    .user(user)
                    .problem(problem)
                    .isCorrect(isCorrect)
                    .userAnswer(item.getAnswer())
                    .timeSpentSec(item.getTimeSpentSec())
                    .xpEarned(isCorrect ? XP_PER_CORRECT : 0)
                    .build());

            // 카테고리 통계 업데이트 (problem.category 문자열 → Topic 매핑, 없으면 생성)
            Topic topic = topicRepository.findByName(problem.getCategory())
                    .orElseGet(() -> topicRepository.save(
                            Topic.builder().name(problem.getCategory()).build()));
            UserCategoryStat stat = categoryStatRepository
                    .findByUserIdAndTopicId(userId, topic.getId())
                    .orElseGet(() -> UserCategoryStat.builder()
                            .user(user).topic(topic).build());
            stat.recordResult(isCorrect);
            categoryStatRepository.save(stat);

            results.add(SubmitResultResponse.ProblemResult.builder()
                    .problemId(problem.getId())
                    .isCorrect(isCorrect)
                    .correctAnswer(problem.getAnswer())
                    .explanation(problem.getExplanation())
                    .build());
        }

        // 스트릭 업데이트
        boolean streakUpdated = updateStreak(user);
        if (streakUpdated) xpEarned += XP_STREAK_BONUS;

        // XP 로그 저장
        if (xpEarned > 0) {
            xpLogRepository.save(UserXpLog.builder()
                    .user(user).xpAmount(xpEarned).reason(XpReason.SOLVE_CORRECT).build());
        }

        return SubmitResultResponse.builder()
                .totalProblems(request.getAnswers().size())
                .correctCount(correctCount)
                .xpEarned(xpEarned)
                .streakUpdated(streakUpdated)
                .currentStreak(user.getStreakDays())
                .results(results)
                .build();
    }

    private boolean checkAnswer(Problem problem, Object userAnswer) {
        if (userAnswer == null || problem.getAnswer() == null) return false;
        return problem.getAnswer().toString().equalsIgnoreCase(userAnswer.toString());
    }

    private boolean updateStreak(User user) {
        LocalDate today = LocalDate.now();
        if (streakLogRepository.findByUserIdAndStreakDate(user.getId(), today).isPresent()) {
            return false; // 오늘 이미 기록됨
        }

        LocalDate yesterday = today.minusDays(1);
        int newStreak = streakLogRepository
                .findByUserIdAndStreakDate(user.getId(), yesterday)
                .map(log -> log.getStreakCount() + 1)
                .orElse(1);

        streakLogRepository.save(UserStreakLog.builder()
                .user(user).streakDate(today).streakCount(newStreak).build());

        return true;
    }
}