package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.DTO.RecommendationResponse.AreaStat;
import com.besenior.harucoding.DTO.RecommendationResponse.CompetencySummary;
import com.besenior.harucoding.DTO.RecommendationResponse.RecommendedProblem;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.ProblemEmbedding;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.ProblemEmbeddingRepository;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 역량 기반 문제 추천 (유사도 검색 강화 버전).
 *
 * 흐름:
 *  1) 풀이 이력 → 문제별 최근 결과(마스터/약점) + 유형별 정답률 산출
 *  2) 역량 요약(정답률·추정 레벨·강/약점 영역)
 *  3) 약점 벡터(틀린 문제 가중평균) + 강점 벡터(맞힌 문제 가중평균) 생성
 *       - 최근에·반복해서 틀린 문제일수록 가중치↑ (지수 감쇠)
 *  4) pgvector ANN으로 약점 벡터 근처 미풀이 후보 검색 (난이도 밴드·언어 필터)
 *  5) 하이브리드 랭킹으로 점수화:
 *       + 약점 유사도   - 강점 유사도(중복 회피)
 *       + 약한 유형 보강 + 난이도 적합도 + 복습 가산
 *  6) MMR로 다양성 확보하며 최종 N개 선택
 *  7) 임베딩/오답 이력이 없으면 구조적(난이도·약점영역) 폴백
 */
@Service
@RequiredArgsConstructor
public class ProblemRecommendationService {

    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;
    private final ProblemRepository problemRepository;
    private final ProblemEmbeddingRepository embeddingRepository;

    private static final int CANDIDATE_POOL = 40;    // ANN 후보 수
    private static final double MMR_LAMBDA = 0.75;    // 관련성 vs 다양성 (높을수록 관련성 우선)
    private static final double RECENCY_DECAY = 0.9;  // 최신성 가중치 감쇠율

    // 하이브리드 랭킹 가중치
    private static final double W_WEAKNESS = 1.00;    // 약점과의 유사도
    private static final double W_STRENGTH = 0.35;    // 강점과의 유사도(감점)
    private static final double W_TYPE     = 0.25;    // 약한 유형 보강
    private static final double W_DIFF     = 0.20;    // 난이도 적합도
    private static final double W_REVIEW   = 0.15;    // 복습(이전 오답) 가산

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1) 이력 로드 (최신순) → 문제별 최근 1건만 유지
        List<UserProblemRecord> records = recordRepository.findByUserIdOrderBySolvedAtDesc(userId);
        Map<Long, UserProblemRecord> latest = new LinkedHashMap<>();
        for (UserProblemRecord r : records) {
            latest.putIfAbsent(r.getProblem().getId(), r);
        }

        // 최근 결과가 정답이면 '마스터'(제외), 오답이면 '약점'(복습) — 모두 최신순 유지
        List<Long> masteredList = latest.values().stream()
                .filter(UserProblemRecord::isCorrect)
                .map(r -> r.getProblem().getId())
                .collect(Collectors.toList());
        List<Long> wrongList = latest.values().stream()
                .filter(r -> !r.isCorrect())
                .map(r -> r.getProblem().getId())
                .collect(Collectors.toList());
        Set<Long> masteredSet = new HashSet<>(masteredList);
        Set<Long> wrongSet = new HashSet<>(wrongList);

        // 유형별 정답률 (구현/디버깅/빈칸)
        Map<String, int[]> typeStats = buildTypeStats(records); // [attempts, correct]

        String lang = user.getPreferredLanguage();   // null 이면 전체 언어
        int level = estimateLevel(user, records);
        int lo = Math.max(0, level - 1);
        int hi = Math.min(2, level + 1);

        // 2) 역량 요약
        CompetencySummary summary = buildSummary(records, level);

        // 3) 약점/강점 벡터 (가중평균)
        double[] weaknessVec = weightedAverageEmbedding(wrongList);
        double[] strengthVec = weightedAverageEmbedding(masteredList);

        // 4~6) 추천 생성: 유사도 우선, 실패 시 구조적 폴백
        List<RecommendedProblem> recs = Collections.emptyList();
        String strategy = null;
        if (weaknessVec != null) {
            recs = similarityRecommend(weaknessVec, strengthVec, masteredSet, wrongSet,
                    typeStats, level, lang, lo, hi, limit);
            if (!recs.isEmpty()) strategy = "SIMILARITY";
        }
        if (recs.isEmpty()) {
            recs = structuredRecommend(masteredSet, summary, lang, lo, hi, limit);
            strategy = records.isEmpty() ? "COLD_START" : "PERSONALIZED";
        }

        return RecommendationResponse.builder()
                .userId(userId)
                .generatedAt(LocalDateTime.now().toString())
                .strategy(strategy)
                .summary(summary)
                .recommendations(recs)
                .build();
    }

    // ── 유사도 기반 추천 (하이브리드 랭킹) ────────────────────────────
    private List<RecommendedProblem> similarityRecommend(double[] weaknessVec,
                                                         double[] strengthVec,
                                                         Set<Long> masteredIds,
                                                         Set<Long> wrongIds,
                                                         Map<String, int[]> typeStats,
                                                         int level,
                                                         String lang, int lo, int hi, int limit) {
        List<Long> exclude = new ArrayList<>(masteredIds);
        exclude.add(-1L); // NOT IN () 빈 리스트 방지

        // 후보 생성: 약점 벡터에 가까운 미풀이 문제 (DB가 ANN으로 처리)
        List<Long> candidateIds = embeddingRepository.findNearestCandidateIds(
                toLiteral(weaknessVec), exclude, lang == null ? "" : lang, lo, hi, CANDIDATE_POOL);

        if (candidateIds.isEmpty()) return Collections.emptyList();

        Map<Long, Problem> problemMap = problemRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
        Map<Long, double[]> embMap = embeddingRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(ProblemEmbedding::getProblemId, e -> parseVector(e.getEmbedding())));

        // 후보별 하이브리드 관련성 점수 미리 계산
        Map<Long, Double> relevance = new HashMap<>();
        for (Long c : candidateIds) {
            Problem p = problemMap.get(c);
            double[] v = embMap.get(c);
            double rel = W_WEAKNESS * cosine(weaknessVec, v)
                    - W_STRENGTH * (strengthVec != null ? cosine(strengthVec, v) : 0.0)
                    + W_TYPE * typeBoost(p.getType().name(), typeStats)
                    + W_DIFF * difficultyFit(p.getDifficulty(), level)
                    + W_REVIEW * (wrongIds.contains(c) ? 1.0 : 0.0);
            relevance.put(c, rel);
        }

        // MMR: 관련성 - 이미 뽑힌 것과의 유사도(다양성)
        List<Long> remaining = new ArrayList<>(candidateIds);
        List<Long> selected = new ArrayList<>();
        while (selected.size() < limit && !remaining.isEmpty()) {
            Long best = null;
            double bestScore = -Double.MAX_VALUE;
            for (Long c : remaining) {
                double div = 0.0;
                for (Long s : selected) {
                    div = Math.max(div, cosine(embMap.get(c), embMap.get(s)));
                }
                double mmr = MMR_LAMBDA * relevance.get(c) - (1 - MMR_LAMBDA) * div;
                if (mmr > bestScore) {
                    bestScore = mmr;
                    best = c;
                }
            }
            selected.add(best);
            remaining.remove(best);
        }

        List<RecommendedProblem> result = new ArrayList<>();
        for (Long id : selected) {
            Problem p = problemMap.get(id);
            double sim = cosine(weaknessVec, embMap.get(id));
            boolean review = wrongIds.contains(id);
            String area = area(p);
            boolean weakType = typeBoost(p.getType().name(), typeStats) > 0.5;

            String reason;
            if (review) {
                reason = "이전에 틀린 문제예요. 복습으로 약점을 다져보세요.";
            } else {
                reason = String.format("취약 영역 '%s'과(와) 유사해요 (유사도 %.0f%%)", area, sim * 100);
                if (weakType) reason += String.format(", 약한 유형 '%s' 보강", typeLabel(p.getType().name()));
            }

            result.add(RecommendedProblem.builder()
                    .problemId(p.getId())
                    .title(p.getTitle())
                    .category(p.getCategory())
                    .subcategory(p.getSubcategory())
                    .difficulty(p.getDifficulty())
                    .type(p.getType().name())
                    .language(p.getLanguage())
                    .similarityScore(round(sim))
                    .matchedWeakness(area)
                    .review(review)
                    .reason(reason)
                    .build());
        }
        return result;
    }

    // ── 구조적 폴백 (임베딩/오답 이력 없을 때) ───────────────────────
    private List<RecommendedProblem> structuredRecommend(Set<Long> masteredIds,
                                                         CompetencySummary summary,
                                                         String lang, int lo, int hi, int limit) {
        Set<String> weakAreas = summary.getWeakAreas().stream()
                .map(AreaStat::getArea).collect(Collectors.toSet());

        return problemRepository.findAll().stream()
                .filter(p -> !masteredIds.contains(p.getId()))
                .filter(p -> lang == null || lang.equals(p.getLanguage()))
                .filter(p -> p.getDifficulty() >= lo && p.getDifficulty() <= hi)
                // 약점 영역 우선, 그다음 난이도 낮은 순
                .sorted(Comparator
                        .comparing((Problem p) -> weakAreas.contains(area(p)) ? 0 : 1)
                        .thenComparingInt(Problem::getDifficulty))
                .limit(limit)
                .map(p -> {
                    boolean weak = weakAreas.contains(area(p));
                    return RecommendedProblem.builder()
                            .problemId(p.getId())
                            .title(p.getTitle())
                            .category(p.getCategory())
                            .subcategory(p.getSubcategory())
                            .difficulty(p.getDifficulty())
                            .type(p.getType().name())
                            .language(p.getLanguage())
                            .similarityScore(0.0)
                            .matchedWeakness(weak ? area(p) : null)
                            .review(false)
                            .reason(weak
                                    ? String.format("취약 영역 '%s' 보완용 문제예요.", area(p))
                                    : String.format("현재 실력(레벨 %d~%d)에 맞는 문제예요.", lo, hi))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── 역량 요약 ────────────────────────────────────────────────────
    private CompetencySummary buildSummary(List<UserProblemRecord> records, int level) {
        long total = records.size();
        long correct = records.stream().filter(UserProblemRecord::isCorrect).count();
        double accuracy = total == 0 ? 0.0 : round1((double) correct / total * 100);

        // 영역(subcategory, 없으면 category)별 집계
        Map<String, int[]> byArea = new HashMap<>(); // [attempts, correct]
        for (UserProblemRecord r : records) {
            String area = area(r.getProblem());
            int[] s = byArea.computeIfAbsent(area, k -> new int[2]);
            s[0]++;
            if (r.isCorrect()) s[1]++;
        }

        List<AreaStat> stats = byArea.entrySet().stream()
                .map(e -> AreaStat.builder()
                        .area(e.getKey())
                        .attempts(e.getValue()[0])
                        .correct(e.getValue()[1])
                        .accuracyRate(round1((double) e.getValue()[1] / e.getValue()[0] * 100))
                        .build())
                .toList();

        List<AreaStat> weak = stats.stream()
                .filter(s -> s.getAttempts() >= 2 && s.getAccuracyRate() < 50.0)
                .sorted(Comparator.comparingDouble(AreaStat::getAccuracyRate))
                .limit(5).toList();

        List<AreaStat> strong = stats.stream()
                .filter(s -> s.getAttempts() >= 2 && s.getAccuracyRate() >= 80.0)
                .sorted(Comparator.comparingDouble(AreaStat::getAccuracyRate).reversed())
                .limit(5).toList();

        return CompetencySummary.builder()
                .totalSolved(total)
                .accuracyRate(accuracy)
                .estimatedLevel(level)
                .weakAreas(weak)
                .strongAreas(strong)
                .build();
    }

    // ── 추정 레벨 (난이도 컨트롤러) ──────────────────────────────────
    private int estimateLevel(User user, List<UserProblemRecord> records) {
        if (records.isEmpty()) {
            int base = switch (user.getCodingLevel() == null ? "NONE" : user.getCodingLevel()) {
                case "LOTS" -> 2;
                case "SOME" -> 1;
                default -> 0;
            };
            if (user.isCotePrepared() && base < 2) base++;
            return base;
        }
        int best = 0;
        for (int d = 0; d <= 2; d++) {
            final int dd = d;
            List<UserProblemRecord> at = records.stream()
                    .filter(r -> r.getProblem().getDifficulty() == dd).toList();
            if (at.size() >= 3) {
                long c = at.stream().filter(UserProblemRecord::isCorrect).count();
                if ((double) c / at.size() >= 0.6) best = d;
            }
        }
        return best;
    }

    // ── 유형 통계/보강 ───────────────────────────────────────────────
    private Map<String, int[]> buildTypeStats(List<UserProblemRecord> records) {
        Map<String, int[]> stats = new HashMap<>();
        for (UserProblemRecord r : records) {
            String type = r.getProblem().getType().name();
            int[] s = stats.computeIfAbsent(type, k -> new int[2]);
            s[0]++;
            if (r.isCorrect()) s[1]++;
        }
        return stats;
    }

    /** 약한 유형일수록 높은 보강 점수(0~1). 미경험 유형은 탐색을 위해 0.7. */
    private double typeBoost(String type, Map<String, int[]> typeStats) {
        int[] s = typeStats.get(type);
        if (s == null || s[0] == 0) return 0.7;
        return 1.0 - (double) s[1] / s[0];
    }

    /** 난이도가 목표 레벨에 가까울수록 1, 멀수록 0 (0/1/2 기준). */
    private double difficultyFit(int difficulty, int level) {
        return 1.0 - Math.abs(difficulty - level) / 2.0;
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "IMPLEMENTATION" -> "구현";
            case "DEBUGGING" -> "디버깅";
            case "FILL_IN_THE_BLANK" -> "빈칸";
            default -> type;
        };
    }

    // ── 임베딩 유틸 ──────────────────────────────────────────────────
    /**
     * 주어진 문제들의 임베딩을 "최신성 가중" 평균. (앞쪽=최근일수록 가중치↑)
     * 임베딩이 하나도 없으면 null.
     */
    private double[] weightedAverageEmbedding(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) return null;

        Map<Long, double[]> embMap = embeddingRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(ProblemEmbedding::getProblemId, e -> parseVector(e.getEmbedding())));
        if (embMap.isEmpty()) return null;

        double[] sum = null;
        double sumW = 0.0;
        for (int i = 0; i < orderedIds.size(); i++) {
            double[] v = embMap.get(orderedIds.get(i));
            if (v == null) continue;
            double w = Math.pow(RECENCY_DECAY, i);   // 최근일수록 큰 가중치
            if (sum == null) sum = new double[v.length];
            for (int j = 0; j < v.length; j++) sum[j] += w * v[j];
            sumW += w;
        }
        if (sum == null || sumW == 0.0) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= sumW;
        return sum;
    }

    private double[] parseVector(String s) {
        String body = s.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        String[] parts = body.split(",");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i].trim());
        return v;
    }

    private String toLiteral(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }

    private double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String area(Problem p) {
        return p.getSubcategory() != null ? p.getSubcategory() : p.getCategory();
    }

    private double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
