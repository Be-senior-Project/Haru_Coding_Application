package com.besenior.harucoding.generation;

import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.DTO.RecommendationResponse.RecommendedProblem;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.global.enums.ProblemCategory;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.global.util.LanguageRules;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.service.ProblemRecommendationService;
import com.besenior.harucoding.service.WeaknessAnalyzer;
import com.besenior.harucoding.service.WeaknessAnalyzer.Weakness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "세트 시작하기"(오늘의 문제) 진입점.
 *
 * 세트 구성:
 *   1) 약점 영역이 있으면 세트의 절반(올림)을 약점 영역에서 아직 풀지 않은 DB 문제로 채운다.
 *      약점 영역은 공용 기준(WeaknessAnalyzer)으로 정한다: 3문제 이상 풀고 첫 제출 정답률 70% 미만인 분류 중 가장 낮은 곳.
 *   2) 나머지는 추천 엔진(안 푼 문제, 약점·난이도·언어 반영)으로 채운다.
 *   3) 그래도 모자라면 부족분만큼 즉석 생성한다. 약점 영역이 있으면 그 분류·하위 분류로, 없으면 기초/입문으로.
 *      생성은 OpenAI 비용이 들어 사용자별 하루 한도 안에서만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSetSessionService {

    private final ProblemRecommendationService recommendationService;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final GenerationPipeline generationPipeline;
    private final GenerationQuota generationQuota;
    /** DB 조회 구간만 감싸는 트랜잭션. 문제 생성은 이 밖에서 한다. */
    private final TransactionTemplate transactionTemplate;

    private static final List<String> ALL_TYPES =
            List.of("Implementation", "Debugging", "Fill-in-the-blank");

    /** 세트 크기 상한. 앱의 하루 목표 최대값(10)과 같다. count를 크게 보내 한 번에 대량 생성하는 것을 막는다. */
    static final int MAX_SET_SIZE = 10;

    /** 약점 영역이 없을 때 생성할 분류 */
    static final String DEFAULT_CATEGORY = ProblemCategory.BASIC.getKey();

    /** DB 조회로 정한 세트 구성. 생성 전에 트랜잭션을 끝내려고 화면 응답(DTO)과 생성 조건만 담는다. */
    private record Plan(List<ProblemResponse> result, Weakness weak, int difficulty, String language) {}

    public List<ProblemResponse> startSet(Long userId, int count) {
        int size = Math.max(1, Math.min(count, MAX_SET_SIZE));
        // 1)·2) DB 조회는 짧은 트랜잭션 안에서 끝낸다
        Plan plan = transactionTemplate.execute(status -> plan(userId, size));
        List<ProblemResponse> result = new ArrayList<>(plan.result());
        Weakness weak = plan.weak();

        // 3) 모자라면 부족분만큼 즉석 생성 → 세트 크기 보장.
        // 트랜잭션 밖에서 생성해 OpenAI 응답(수십 초)을 기다리는 동안 DB 연결을 붙잡지 않는다(동시 요청 시 연결 고갈 방지).
        int need = size - result.size();
        if (need <= 0) {
            log.info("세트 시작: DB {}문제 서빙 (user {}, 약점 영역 {})",
                    result.size(), userId, weak == null ? "없음" : weak.area());
            return result;
        }
        // 생성은 OpenAI 비용이 들어 사용자별 하루 한도를 넘으면 하지 않는다
        if (!generationQuota.tryConsume(userId, need)) {
            if (result.isEmpty()) {
                throw new CustomException(ErrorCode.GENERATION_LIMIT_EXCEEDED);
            }
            log.info("세트 시작: 생성 한도 초과로 DB 문제 {}개만 서빙 (user {})", result.size(), userId);
            return result;
        }
        String category = weak != null ? weak.category() : DEFAULT_CATEGORY;
        String subcategory = weak != null ? weak.subcategory() : null;
        log.info("세트 시작: DB {}문제 + 생성 {}문제 (user {}, 분류 {}/{}, diff {}, lang {})",
                result.size(), need, userId, category, subcategory, plan.difficulty(), plan.language());
        List<Problem> generated = generationPipeline.generateAndSave(
                category, subcategory, plan.difficulty(), plan.language(), ALL_TYPES, need);
        generated.forEach(p -> result.add(toResponse(p, weak)));
        return result;
    }

    /** 약점 몫(1)과 추천 몫(2)을 DB에서 고른다. startSet이 트랜잭션 안에서 부른다. */
    private Plan plan(Long userId, int count) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<UserProblemRecord> records = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);
        Weakness weak = WeaknessAnalyzer.find(records);
        RecommendationResponse rec = recommendationService.recommend(userId, count);
        int difficulty = rec.getSummary() != null ? Math.min(rec.getSummary().getEstimatedLevel(), 2) : 0;
        String language = LanguageRules.problemLanguage(user.getPreferredLanguage());

        List<ProblemResponse> result = new ArrayList<>();
        Set<Long> chosen = new HashSet<>();

        // 1) 약점 영역 몫: 세트의 절반(올림). 약점 영역에서 아직 풀지 않은 DB 문제
        int weakQuota = weak == null ? 0 : (count + 1) / 2;
        if (weak != null) {
            Set<Long> attempted = records.stream().map(r -> r.getProblem().getId()).collect(Collectors.toSet());
            for (Problem p : unsolvedInWeakArea(weak, language, difficulty, attempted, weakQuota)) {
                result.add(toResponse(p, weak));
                chosen.add(p.getId());
            }
        }

        // 2) 나머지 몫: 추천 엔진. 약점 몫이 모자라도 여기서 채우지 않고 3)에서 약점 영역으로 생성한다.
        List<Long> ids = rec.getRecommendations().stream()
                .map(RecommendedProblem::getProblemId)
                .filter(id -> !chosen.contains(id))
                .limit(count - weakQuota)
                .toList();
        if (!ids.isEmpty()) {
            Map<Long, Problem> byId = problemRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Problem::getId, p -> p));
            ids.stream().map(byId::get).filter(Objects::nonNull)
                    .forEach(p -> {
                        result.add(toResponse(p, weak));
                        chosen.add(p.getId());
                    });
        }
        return new Plan(result, weak, difficulty, language);
    }

    /**
     * 약점 영역에서 아직 풀지 않은 문제. 언어가 같고 난이도가 사용자 레벨 ±1 안인 문제만 고르며,
     * 약점 하위 분류와 같은 문제, 레벨에 가까운 난이도 순으로 앞에 둔다.
     */
    private List<Problem> unsolvedInWeakArea(Weakness weak, String language, int level,
                                             Set<Long> attempted, int limit) {
        return problemRepository.findByCategory(weak.category()).stream()
                .filter(p -> !attempted.contains(p.getId()))
                .filter(p -> language.equals(p.getLanguage()))
                .filter(p -> Math.abs(p.getDifficulty() - level) <= 1)
                .sorted(Comparator
                        .comparing((Problem p) -> Objects.equals(weak.subcategory(), p.getSubcategory()) ? 0 : 1)
                        .thenComparingInt(p -> Math.abs(p.getDifficulty() - level)))
                .limit(limit)
                .toList();
    }

    /** 약점 분류의 문제면 출제 이유(약점 영역)를 담는다. 앱은 이 값으로 "약점 보완" 표시를 한다. */
    private ProblemResponse toResponse(Problem p, Weakness weak) {
        ProblemResponse r = ProblemResponse.from(p, false);
        if (weak == null || !weak.category().equals(p.getCategory())) {
            return r;
        }
        String area = p.getSubcategory() != null ? p.getSubcategory() : p.getCategory();
        return r.toBuilder().weakArea(area).build();
    }
}
