package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.ProblemSetResponse;
import com.besenior.harucoding.DTO.SubmitAnswerRequest;
import com.besenior.harucoding.DTO.SubmitResultResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.generation.GenerationPipeline;
import com.besenior.harucoding.generation.GenerationQuota;
import com.besenior.harucoding.global.util.DifficultyRules;
import com.besenior.harucoding.global.util.LanguageRules;
import com.besenior.harucoding.global.enums.XpReason;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.global.util.AnswerLimits;
import com.besenior.harucoding.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemSetService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProblemSetRepository problemSetRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final UserCategoryStatRepository categoryStatRepository;
    private final UserXpLogRepository xpLogRepository;
    private final TopicRepository topicRepository;
    private final GenerationPipeline generationPipeline;
    private final GenerationQuota generationQuota;
    private final RecommendationService recommendationService;

    private static final int XP_PER_CORRECT = 10;
    private static final int XP_STREAK_BONUS = 5;
    private static final int PROBLEMS_PER_SET = 3;
    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    @Transactional(readOnly = true)
    public ProblemSetResponse getTodaySet() {
        ProblemSet set = problemSetRepository
                .findByTargetDateWithItems(LocalDate.now(KST))
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_SET_NOT_FOUND));
        return ProblemSetResponse.from(set, false); // 정답 미포함
    }

    /**
     * 오늘의 세트를 반환하되, 없으면 LLM으로 생성해 problem_sets로 묶어 저장한다.
     * 생성 난이도·언어는 유저의 온보딩 추천값(recommended_difficulty / preferred_language)을 반영.
     * 날짜당 1세트(target_date UNIQUE)라 첫 호출자만 생성하고 이후엔 같은 세트를 공유한다.
     */
    @Transactional
    public ProblemSetResponse ensureTodaySet(Long userId) {
        LocalDate today = LocalDate.now(KST);

        var existing = problemSetRepository.findByTargetDateWithItems(today);
        if (existing.isPresent()) {
            return ProblemSetResponse.from(existing.get(), false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // recommended_difficulty를 읽어 쓰던 자리. 컬럼은 DB에 남아 있지만 서버는 쓰지 않고,
        // 원본 답변(coding_level/cote_prepared)에서 매번 계산한다.
        int difficulty = recommendationService.onboardingBaseLevel(
                user.getCodingLevel(), user.isCotePrepared());
        String language = LanguageRules.problemLanguage(user.getPreferredLanguage());
        String category = difficulty == 0 ? "Basic/Introductory" : "Algorithm/Data Structure";

        // 생성에 실패하면 다음 호출자가 다시 생성을 시도하므로, 반복 호출로 비용이 쌓이지 않게 한도를 건다
        if (!generationQuota.tryConsume(userId, PROBLEMS_PER_SET)) {
            throw new CustomException(ErrorCode.GENERATION_LIMIT_EXCEEDED);
        }
        List<Problem> problems = generationPipeline.generateAndSave(
                category, null, difficulty, language, ALL_TYPES, PROBLEMS_PER_SET);
        if (problems.isEmpty()) {
            throw new CustomException(ErrorCode.SET_GENERATION_FAILED);
        }

        ProblemSet set = ProblemSet.builder()
                .title("오늘의 코딩 도전")
                .targetDate(today)
                .difficulty(DifficultyRules.toSetLevel(difficulty))
                .isAiGenerated(true)
                .build();
        for (int i = 0; i < problems.size(); i++) {
            set.getItems().add(ProblemSetItem.builder()
                    .problemSet(set).problem(problems.get(i)).sortOrder(i).build());
        }
        problemSetRepository.save(set); // cascade로 items 함께 저장

        return ProblemSetResponse.from(set, false);
    }


    @Transactional
    public SubmitResultResponse submit(Long userId, Long setId, SubmitAnswerRequest request) {
        if (request.getAnswers() != null) {
            AnswerLimits.checkAll(request.getAnswers());
            request.getAnswers().forEach(item -> AnswerLimits.check(item.getAnswer()));
        }
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
            // XP는 문제별 첫 정답에만 준다(이번 기록을 저장하기 전에 확인). 단건 제출(ProblemService)과 같은 기준.
            boolean firstCorrect = isCorrect && !recordRepository.existsCorrect(userId, problem.getId());
            if (isCorrect) {
                correctCount++;
            }
            if (firstCorrect) {
                xpEarned += XP_PER_CORRECT;
            }

            // 풀이 기록 저장
            recordRepository.save(UserProblemRecord.builder()
                    .user(user)
                    .problem(problem)
                    .isCorrect(isCorrect)
                    .userAnswer(item.getAnswer())
                    .timeSpentSec(item.getTimeSpentSec())
                    .xpEarned(firstCorrect ? XP_PER_CORRECT : 0)
                    .build());

            // 카테고리 통계 업데이트 (problem.category 문자열 → Topic). 분류 3개의 주제는 서버 시작 때 만들어 두므로
            // (TopicInitializer) 여기서 새로 만들지 않는다.
            topicRepository.findByName(problem.getCategory()).ifPresent(topic -> {
                UserCategoryStat stat = categoryStatRepository
                        .findByUserIdAndTopicId(userId, topic.getId())
                        .orElseGet(() -> UserCategoryStat.builder()
                                .user(user).topic(topic).build());
                stat.recordResult(isCorrect);
                categoryStatRepository.save(stat);
            });

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

        // XP 로그 저장 + User 반영
        if (xpEarned > 0) {
            xpLogRepository.save(UserXpLog.builder()
                    .user(user).xpAmount(xpEarned).reason(XpReason.SOLVE_CORRECT).build());
            user.addXp(xpEarned);
        }

        int currentStreak = getCurrentStreak(user.getId());
        user.updateStreakDays(currentStreak);

        return SubmitResultResponse.builder()
                .totalProblems(request.getAnswers().size())
                .correctCount(correctCount)
                .xpEarned(xpEarned)
                .streakUpdated(streakUpdated)
                .currentStreak(currentStreak)
                .results(results)
                .build();
    }

    private boolean checkAnswer(Problem problem, Object userAnswer) {
        if (userAnswer == null || problem.getAnswer() == null) return false;
        return problem.getAnswer().toString().equalsIgnoreCase(userAnswer.toString());
    }

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

    private boolean updateStreak(User user) {
        LocalDate today = LocalDate.now(KST);
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