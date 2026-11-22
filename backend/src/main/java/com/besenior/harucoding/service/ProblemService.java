package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.AttemptRequest;
import com.besenior.harucoding.DTO.AttemptResultResponse;
import com.besenior.harucoding.DTO.HintResponse;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.global.enums.XpReason;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.global.util.AnswerLimits;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.GradeResult;
import com.besenior.harucoding.generation.verify.VerifyResult;
import com.besenior.harucoding.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final UserStreakLogRepository streakLogRepository;
    private final UserCategoryStatRepository categoryStatRepository;
    private final UserXpLogRepository xpLogRepository;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;
    private final CodeVerifier codeVerifier;
    private final LeagueService leagueService;

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
    public HintResponse getHint(Long id) {
        Problem p = problemRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));
        return HintResponse.builder()
                .hint1(p.getHint1())
                .hint2(p.getHint2())
                .hint3(p.getHint3())
                .build();
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
        AnswerLimits.check(request.getAnswer());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));

        GradeOutcome outcome = grade(problem, request.getAnswer());
        boolean correct = outcome.correct();
        // XP는 문제별 첫 정답에만 준다. 같은 문제를 반복 제출해 XP·리그 점수를 올리지 못하게 하기 위함.
        // (이번 기록을 저장하기 전에 확인해야 한다)
        boolean firstCorrect = correct && !recordRepository.existsCorrect(userId, problemId);
        int xpEarned = 0;

        // 풀이 기록 저장 (추천 알고리즘의 신호원)
        recordRepository.save(UserProblemRecord.builder()
                .user(user)
                .problem(problem)
                .isCorrect(correct)
                .userAnswer(toJsonSafe(request.getAnswer()))
                .timeSpentSec(request.getTimeSpentSec())
                .xpEarned(firstCorrect ? XP_PER_CORRECT : 0)
                .build());

        // 카테고리 통계 (category 문자열 → Topic). 분류 3개의 주제는 서버 시작 때 만들어 두므로(TopicInitializer)
        // 여기서 새로 만들지 않는다. 예전엔 없으면 영어 이름 주제를 만들어 홈 주제 카드가 늘어났다.
        topicRepository.findByName(problem.getCategory()).ifPresent(topic -> {
            UserCategoryStat stat = categoryStatRepository
                    .findByUserIdAndTopicId(userId, topic.getId())
                    .orElseGet(() -> UserCategoryStat.builder().user(user).topic(topic).build());
            stat.recordResult(correct);
            categoryStatRepository.save(stat);
        });

        // 스트릭 + XP (다시 맞힌 문제도 오늘의 학습으로 연속 학습일에는 반영한다)
        if (correct) {
            if (firstCorrect) xpEarned += XP_PER_CORRECT;
            if (updateStreak(user)) xpEarned += XP_STREAK_BONUS;
            if (xpEarned > 0) {
                xpLogRepository.save(UserXpLog.builder()
                        .user(user).xpAmount(xpEarned).reason(XpReason.SOLVE_CORRECT).build());
                user.addXp(xpEarned);
                leagueService.addScore(userId, xpEarned);
            }
        }

        int currentStreak = getCurrentStreak(userId);
        user.updateStreakDays(currentStreak);

        // 오답 이유: 실행 채점일 때만 있다. GradeResult가 이미 숨겨진 테스트 내용을 뺀 값만 채워 둔다.
        GradeResult fail = correct ? null : outcome.result();
        return AttemptResultResponse.builder()
                .problemId(problem.getId())
                .correct(correct)
                .correctAnswer(problem.getAnswer())
                .explanation(problem.getExplanation())
                .xpEarned(xpEarned)
                .currentStreak(currentStreak)
                .testsPassed(outcome.passed())
                .testsTotal(outcome.total())
                .failReason(fail == null ? null : fail.reason())
                .failedTest(fail == null ? null : fail.failedTest())
                .failMessage(fail == null ? null : fail.message())
                .expectedOutput(fail == null ? null : fail.expectedOutput())
                .actualOutput(fail == null ? null : fail.actualOutput())
                .build();
    }

    // ── 코드 실행 (SOLVE-007, 채점 아님 — 이력에 남기지 않는다) ──
    // 예시 입출력만 실행한다. 숨겨진 테스트는 결과에 드러나면 안 되므로 여기서 쓰지 않는다.
    @Transactional(readOnly = true)
    public VerifyResult run(Long problemId, Object answer) {
        AnswerLimits.check(answer);
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROBLEM_NOT_FOUND));

        ObjectNode node = buildVerifyNode(problem, answer);
        if (node == null) {
            return VerifyResult.fail("internal_error", "이 문제는 코드 실행을 지원하지 않아요.");
        }
        return codeVerifier.verify(node, problem.getLanguage());
    }

    // ── 채점 ─────────────────────────────────────────────────────
    /**
     * 채점 결과. passed/total은 통과한 테스트 수 / 전체 테스트 수(예시 포함).
     * result는 코드를 실행해 채점했을 때의 상세(오답 이유 포함). 문자열 비교로 채점했으면 null.
     */
    private record GradeOutcome(boolean correct, int passed, int total, GradeResult result) {}

    /**
     * 채점: 유저 코드를 예시 + 숨겨진 테스트로 실제 실행해 모두 맞으면 정답.
     * 숨겨진 테스트가 없는 예전 문제는 예시로만 채점된다. 단, 숨겨진 테스트가 없는 빈칸 문제는 빈칸별 문자열 비교.
     * (실행 불가 — 스켈레톤/시그니처/IO 누락 또는 내부오류 — 시에만 문자열 비교로 폴백)
     */
    private GradeOutcome grade(Problem problem, Object userAnswer) {
        // 숨겨진 테스트가 없는 빈칸 문제: 예시 1개로 실행 채점하면 틀린 답을 거르지 못하므로 빈칸별 문자열 비교.
        // 숨겨진 테스트가 있으면 아래 실행 채점을 써서, 의미가 같은 다른 표현(x == n 대신 n == x 등)도 정답 처리한다.
        if (problem.getType() == ProblemType.FILL_IN_THE_BLANK && !hasHiddenTests(problem)) {
            log.info("채점[빈칸비교]: 숨겨진 테스트가 없어 빈칸별 문자열 비교 사용");
            boolean correct = checkAnswer(problem.getAnswer(), userAnswer);
            return new GradeOutcome(correct, correct ? 1 : 0, 1, null);
        }
        try {
            ObjectNode node = buildVerifyNode(problem, userAnswer);
            if (node != null) {
                GradeResult r = codeVerifier.grade(node, problem.getLanguage(), problem.getHiddenTests());
                log.info("채점[실행]: type={} lang={} ok={} 통과 {}/{} reason={} detail={}",
                        problem.getType(), problem.getLanguage(), r.ok(), r.passed(), r.total(), r.reason(), r.detail());
                // 우리 쪽 조립/시그니처 오류면 실행으로 판단 불가 → 문자열 비교로 폴백
                if (!"internal_error".equals(r.reason())) {
                    return new GradeOutcome(r.ok(), r.passed(), r.total(), r);
                }
            } else {
                log.warn("채점[폴백]: 실행 불가 (스켈레톤/시그니처/IO 누락)");
            }
        } catch (Exception e) {
            log.warn("채점[폴백]: 실행 채점 예외 → 문자열 비교", e);
        }
        log.info("채점[문자열비교] 사용");
        boolean correct = checkAnswer(problem.getAnswer(), userAnswer);
        return new GradeOutcome(correct, correct ? 1 : 0, 1, null);
    }

    /** CodeVerifier.verify()에 넘길 검증용 JSON 조립. 실행 불가(스켈레톤/시그니처/IO 누락)면 null. */
    private ObjectNode buildVerifyNode(Problem problem, Object userAnswer) {
        String skeleton = problem.getCodeSkeleton();
        Map<String, String> io = problem.getIoExample();
        String signature = skeleton == null ? null : extractSignature(skeleton);
        if (signature == null || io == null || io.get("output") == null) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("Type", typeName(problem.getType()));
        node.put("Signature", signature);
        ObjectNode ioNode = node.putObject("IO Example");
        ioNode.put("input", io.getOrDefault("input", ""));
        ioNode.put("output", io.get("output"));
        node.put("Code Skeleton", skeleton);
        // 구현 답안의 들여쓰기는 CodeComposer가 {{CORE}} 위치에 맞춰 붙인다. 여기서 또 붙이면 두 번 들여써진다.
        node.set("Answer", objectMapper.valueToTree(userAnswer));
        return node;
    }

    private static boolean hasHiddenTests(Problem problem) {
        return problem.getHiddenTests() != null && !problem.getHiddenTests().isEmpty();
    }

    private String typeName(ProblemType t) {
        return switch (t) {
            case IMPLEMENTATION -> "Implementation";
            case DEBUGGING -> "Debugging";
            case FILL_IN_THE_BLANK -> "Fill-in-the-blank";
        };
    }

    /** Code Skeleton에서 solution 시그니처 줄을 뽑는다(파라미터 추출용). */
    private String extractSignature(String skeleton) {
        for (String line : skeleton.split("\n")) {
            if (line.contains("solution(")) {
                return line.trim();
            }
        }
        return null;
    }

    /** 코드 문자열을 jsonb 컬럼에 안전 저장: String이면 JSON 인코딩(따옴표/이스케이프), 배열/맵은 그대로. */
    private Object toJsonSafe(Object answer) {
        if (answer instanceof String s) {
            try {
                return objectMapper.writeValueAsString(s);
            } catch (Exception e) {
                return "\"\"";
            }
        }
        return answer;
    }

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
        // 빈칸 답: 모든 공백 제거 + 소문자 (i + 1 == i+1)
        return o == null ? "" : o.toString().replaceAll("\\s+", "").toLowerCase();
    }

    private String normCode(String s) {
        // 코드 답: 공백·줄바꿈·들여쓰기 차이 무시 (모든 공백 제거 후 비교)
        return s == null ? "" : s.replaceAll("\\s+", "");
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

    // ── 스트릭 갱신 (오늘 첫 정답이면 true) ──────────────────────
    private boolean updateStreak(User user) {
        LocalDate today = LocalDate.now(KST);
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
