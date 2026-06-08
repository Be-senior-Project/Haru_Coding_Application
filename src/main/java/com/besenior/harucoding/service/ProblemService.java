package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.AttemptRequest;
import com.besenior.harucoding.DTO.AttemptResultResponse;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.global.enums.XpReason;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final UserCategoryStatRepository categoryStatRepository;
    private final UserXpLogRepository xpLogRepository;
    private final TopicRepository topicRepository;

    private static final int XP_PER_CORRECT = 10;
    private static final int XP_STREAK_BONUS = 5;

    // ── 조회 ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ProblemResponse getProblem(Long id) {
        Problem p = problemRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));
        return ProblemResponse.from(p, false); // 풀이 전: 정답 미포함
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> search(String category, Integer difficulty,
                                        ProblemType type, String language, int limit) {
        return problemRepository
                .search(category, difficulty, type, language, PageRequest.of(0, limit))
                .stream()
                .map(p -> ProblemResponse.from(p, false))
                .toList();
    }

    // ── 풀이 제출(채점 + 기록) ───────────────────────────────────
    @Transactional
    public AttemptResultResponse attempt(Long userId, Long problemId, AttemptRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));

        boolean correct = checkAnswer(problem.getAnswer(), request.getAnswer());
        int xpEarned = 0;

        // 풀이 기록 저장 (추천 알고리즘의 신호원)
        recordRepository.save(UserProblemRecord.builder()
                .user(user)
                .problem(problem)
                .isCorrect(correct)
                .userAnswer(request.getAnswer())
                .timeSpentSec(request.getTimeSpentSec())
                .xpEarned(correct ? XP_PER_CORRECT : 0)
                .build());

        // 카테고리 통계 (category 문자열 → Topic 매핑, 없으면 생성)
        Topic topic = topicRepository.findByName(problem.getCategory())
                .orElseGet(() -> topicRepository.save(
                        Topic.builder().name(problem.getCategory()).build()));
        UserCategoryStat stat = categoryStatRepository
                .findByUserIdAndTopicId(userId, topic.getId())
                .orElseGet(() -> UserCategoryStat.builder().user(user).topic(topic).build());
        stat.recordResult(correct);
        categoryStatRepository.save(stat);

        // 스트릭 + XP
        if (correct) {
            xpEarned += XP_PER_CORRECT;
            if (updateStreak(user)) xpEarned += XP_STREAK_BONUS;
            xpLogRepository.save(UserXpLog.builder()
                    .user(user).xpAmount(xpEarned).reason(XpReason.SOLVE_CORRECT).build());
        }

        int currentStreak = streakLogRepository.findLatestByUserId(userId)
                .map(UserStreakLog::getStreakCount)
                .orElse(0);

        return AttemptResultResponse.builder()
                .problemId(problem.getId())
                .correct(correct)
                .correctAnswer(problem.getAnswer())
                .explanation(problem.getExplanation())
                .xpEarned(xpEarned)
                .currentStreak(currentStreak)
                .build();
    }

    // ── 채점 ─────────────────────────────────────────────────────
    private boolean checkAnswer(Object correct, Object user) {
        if (correct == null || user == null) return false;

        // 빈칸: 둘 다 배열이면 원소별 비교
        if (correct instanceof List<?> c && user instanceof List<?> u) {
            if (c.size() != u.size()) return false;
            for (int i = 0; i < c.size(); i++) {
                if (!norm(c.get(i)).equals(norm(u.get(i)))) return false;
            }
            return true;
        }
        // 구현/디버깅: 코드 정규화 후 비교
        return normCode(correct.toString()).equals(normCode(user.toString()));
    }

    private String norm(Object o) {
        return o == null ? "" : o.toString().trim().toLowerCase();
    }

    private String normCode(String s) {
        return s.replace("\r", "")
                .lines()
                .map(l -> l.replaceAll("\\s+$", ""))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .trim();
    }

    // ── 스트릭 갱신 (오늘 첫 정답이면 true) ──────────────────────
    private boolean updateStreak(User user) {
        LocalDate today = LocalDate.now();
        if (streakLogRepository.findByUserIdAndStreakDate(user.getId(), today).isPresent()) {
            return false;
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
