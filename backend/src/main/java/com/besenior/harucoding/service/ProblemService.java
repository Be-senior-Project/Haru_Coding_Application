package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.AttemptRequest;
import com.besenior.harucoding.DTO.AttemptResultResponse;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.entity.*;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.global.enums.XpReason;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.generation.verify.CodeVerifier;
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
import java.util.List;
import java.util.Map;

@Slf4j
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
    private final ObjectMapper objectMapper;
    private final CodeVerifier codeVerifier;

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

        boolean correct = grade(problem, request.getAnswer());
        int xpEarned = 0;

        // 풀이 기록 저장 (추천 알고리즘의 신호원)
        recordRepository.save(UserProblemRecord.builder()
                .user(user)
                .problem(problem)
                .isCorrect(correct)
                .userAnswer(toJsonSafe(request.getAnswer()))
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
    /**
     * 채점: 유저 코드를 실제로 실행해 IO 출력이 맞으면 정답.
     * (실행 불가 — 스켈레톤/시그니처/IO 누락 또는 내부오류 — 시에만 문자열 비교로 폴백)
     */
    private boolean grade(Problem problem, Object userAnswer) {
        try {
            String skeleton = problem.getCodeSkeleton();
            Map<String, String> io = problem.getIoExample();
            String signature = skeleton == null ? null : extractSignature(skeleton);
            if (signature != null && io != null && io.get("output") != null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("Type", typeName(problem.getType()));
                node.put("Signature", signature);
                ObjectNode ioNode = node.putObject("IO Example");
                ioNode.put("input", io.getOrDefault("input", ""));
                ioNode.put("output", io.get("output"));
                node.put("Code Skeleton", skeleton);
                // 구현: 유저가 0칸부터 쓴 코어를 {{CORE}} 위치 들여쓰기에 맞춰 재정렬
                Object answerForVerify = userAnswer;
                if (problem.getType() == ProblemType.IMPLEMENTATION && userAnswer instanceof String s) {
                    answerForVerify = reindentCore(s, skeleton);
                }
                node.set("Answer", objectMapper.valueToTree(answerForVerify));

                VerifyResult r = codeVerifier.verify(node, problem.getLanguage());
                log.info("채점[실행]: type={} lang={} ok={} reason={} detail={}",
                        problem.getType(), problem.getLanguage(), r.ok(), r.reason(), r.detail());
                // 우리 쪽 조립/시그니처 오류면 실행으로 판단 불가 → 문자열 비교로 폴백
                if (!"internal_error".equals(r.reason())) {
                    return r.ok();
                }
            } else {
                log.warn("채점[폴백]: 실행 불가 (signature={} io={} output={})",
                        signature != null, io != null, io != null ? io.get("output") : null);
            }
        } catch (Exception e) {
            log.warn("채점[폴백]: 실행 채점 예외 → 문자열 비교", e);
        }
        log.info("채점[문자열비교] 사용");
        return checkAnswer(problem.getAnswer(), userAnswer);
    }

    /**
     * 구현 코어 재들여쓰기: 유저 코어를 공통 들여쓰기 기준 0으로 맞춘 뒤,
     * Code Skeleton의 {{CORE}} 라인 들여쓰기(baseIndent)를 모든 줄에 붙여 절대 위치로 정렬.
     */
    private String reindentCore(String answer, String skeleton) {
        String base = "";
        for (String line : skeleton.split("\n")) {
            int idx = line.indexOf("{{CORE}}");
            if (idx >= 0) { base = line.substring(0, idx); break; }
        }
        String[] lines = answer.replace("\r", "").split("\n");
        int min = Integer.MAX_VALUE;
        for (String l : lines) {
            if (l.isBlank()) continue;
            int lead = 0;
            while (lead < l.length() && l.charAt(lead) == ' ') lead++;
            min = Math.min(min, lead);
        }
        if (min == Integer.MAX_VALUE) min = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            sb.append(l.isBlank() ? "" : base + l.substring(Math.min(min, l.length())));
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
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
