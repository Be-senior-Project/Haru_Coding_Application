package com.besenior.harucoding.generation;

import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.DTO.RecommendationResponse.RecommendedProblem;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.service.ProblemRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * "세트 시작하기" 진입점.
 *
 * 정책(반응형 하이브리드):
 *   1) 추천 엔진으로 '안 푼 문제'(약점·난이도·언어 반영) 조회 → 있으면 즉시 서빙
 *   2) 줄 문제가 없으면(DB 비었거나 이 유저가 다 품) → 그때만 즉석 생성 후 서빙
 *
 * 추천 엔진이 이미 user_problem_records(idx_upr_user)로 푼 문제를 제외하므로 별도 인덱스 불필요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSetSessionService {

    private final ProblemRecommendationService recommendationService;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final GenerationPipeline generationPipeline;
    private final GenerationQuota generationQuota;

    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    /** 세트 크기 상한. 앱의 하루 목표 최대값(10)과 같다. count를 크게 보내 한 번에 대량 생성하는 것을 막는다. */
    static final int MAX_SET_SIZE = 10;

    @Transactional
    public List<ProblemResponse> startSet(Long userId, int count) {
        count = Math.max(1, Math.min(count, MAX_SET_SIZE));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<ProblemResponse> result = new ArrayList<>();

        // 1) 추천(= 안 푼 DB 문제)로 세트 채움
        RecommendationResponse rec = recommendationService.recommend(userId, count);
        List<Long> ids = rec.getRecommendations().stream()
                .map(RecommendedProblem::getProblemId)
                .toList();
        if (!ids.isEmpty()) {
            Map<Long, Problem> byId = problemRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Problem::getId, p -> p));
            ids.stream().map(byId::get).filter(Objects::nonNull)
                    .map(p -> ProblemResponse.from(p, false))
                    .forEach(result::add);
        }

        // 2) count에 못 미치면(DB 부족/소진) 부족분만큼 즉석 생성으로 채움 → 세트 크기 보장
        int need = count - result.size();
        if (need > 0) {
            // 생성은 OpenAI 비용이 들어 사용자별 하루 한도를 넘으면 하지 않는다
            if (!generationQuota.tryConsume(userId, need)) {
                if (result.isEmpty()) {
                    throw new CustomException(ErrorCode.GENERATION_LIMIT_EXCEEDED);
                }
                log.info("세트 시작: 생성 한도 초과로 DB 문제 {}개만 서빙 (user {})", result.size(), userId);
                return result;
            }
            int difficulty = rec.getSummary() != null ? Math.min(rec.getSummary().getEstimatedLevel(), 2) : 0;
            String language = mapLanguage(user.getPreferredLanguage());
            log.info("세트 시작: DB {}문제 + 생성 {}문제 (user {}, diff {}, lang {})",
                    result.size(), need, userId, difficulty, language);
            List<Problem> generated = generationPipeline.generateAndSave(
                    "Basic/Introductory", null, difficulty, language, ALL_TYPES, need);
            generated.forEach(p -> result.add(ProblemResponse.from(p, false)));
        } else {
            log.info("세트 시작: DB 추천 {}문제 서빙 (user {})", result.size(), userId);
        }

        return result;
    }

    /** users.preferred_language(JAVA/PYTHON/C/JS) → problems.language(Python/Java/C++) */
    private String mapLanguage(String pref) {
        if (pref == null) return "Python";
        return switch (pref) {
            case "JAVA" -> "Java";
            case "PYTHON" -> "Python";
            case "C" -> "C++";
            default -> "Python";
        };
    }
}
