package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.ProblemSetResponse;
import com.besenior.harucoding.DTO.SubmitAnswerRequest;
import com.besenior.harucoding.DTO.SubmitResultResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.generation.GenerationPipeline;
import com.besenior.harucoding.global.enums.DifficultyLevel;
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
    private final GenerationPipeline generationPipeline;

    private static final int XP_PER_CORRECT = 10;
    private static final int XP_STREAK_BONUS = 5;
    private static final int PROBLEMS_PER_SET = 3;
    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    @Transactional(readOnly = true)
    public ProblemSetResponse getTodaySet() {
        ProblemSet set = problemSetRepository
                .findByTargetDateWithItems(LocalDate.now())
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
        LocalDate today = LocalDate.now();

        var existing = problemSetRepository.findByTargetDateWithItems(today);
        if (existing.isPresent()) {
            return ProblemSetResponse.from(existing.get(), false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        int difficulty = mapDifficulty(user.getRecommendedDifficulty());
        String language = mapLanguage(user.getPreferredLanguage());
        String category = difficulty == 0 ? "Basic/Introductory" : "Algorithm/Data Structure";

        List<Problem> problems = generationPipeline.generateAndSave(
                category, null, difficulty, language, ALL_TYPES, PROBLEMS_PER_SET);
        if (problems.isEmpty()) {
            throw new CustomException(ErrorCode.SET_GENERATION_FAILED);
        }

        ProblemSet set = ProblemSet.builder()
                .title("오늘의 코딩 도전")
                .targetDate(today)
                .difficulty(toLevel(difficulty))
                .isAiGenerated(true)
                .build();
        for (int i = 0; i < problems.size(); i++) {
            set.getItems().add(ProblemSetItem.builder()
                    .problemSet(set).problem(problems.get(i)).sortOrder(i).build());
        }
        problemSetRepository.save(set); // cascade로 items 함께 저장

        return ProblemSetResponse.from(set, false);
    }

    // 추천 난이도 라벨(입문/초급/중급/고급) → 생성 난이도(0/1/2)
    private int mapDifficulty(String recommended) {
        if (recommended == null) return 0;
        return switch (recommended) {
            case "초급" -> 1;
            case "중급", "고급" -> 2;
            default -> 0; // 입문 및 미설정
        };
    }

    // 생성 난이도(0/1/2) → 세트 라벨용 DifficultyLevel
    private DifficultyLevel toLevel(int difficulty) {
        return switch (difficulty) {
            case 1 -> DifficultyLevel.초급;
            case 2 -> DifficultyLevel.중급;
            default -> DifficultyLevel.입문;
        };
    }

    // 선호 언어(JAVA/PYTHON/C/JS) → 생성 언어(Python/Java/C++). 생성기는 JS 미지원 → Python 폴백
    private String mapLanguage(String preferred) {
        if (preferred == null) return "Python";
        return switch (preferred) {
            case "JAVA" -> "Java";
            case "C" -> "C++";
            case "PYTHON", "JS" -> "Python";
            default -> "Python";
        };
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