package com.besenior.harucoding.service;

import com.besenior.harucoding.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.besenior.harucoding.DTO.CategoryStatDto;
import com.besenior.harucoding.DTO.RecommendationFilterDto;
import com.besenior.harucoding.DTO.UserProfileDto;
import com.besenior.harucoding.global.util.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final PromptLoader promptLoader;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GPT_MODEL = "gpt-4o-mini";
    private static final int AI_THRESHOLD = 10; // 이 문제 수 이상이면 AI 추천

    // ── 온보딩 추천 (신규 유저) ────────────────────────────────────
    public RecommendationFilterDto recommendOnboarding(UserProfileDto profile) {
        int score = calcOnboardingScore(profile);
        String difficulty = scoreToDifficulty(score);

        RecommendationFilterDto tempResult;
        try {
            Map<String, String> vars = PromptLoader.vars(
                    "coding_level",       profile.getCodingLevel(),
                    "coding_level_label", codingLevelLabel(profile.getCodingLevel()),
                    "cote_prepared_label", profile.isCotePrepared() ? "있음" : "없음",
                    "preferred_language", profile.getPreferredLanguage() != null
                            ? profile.getPreferredLanguage() : "미설정",
                    "score",              String.valueOf(score)
            );
            tempResult = callGpt("onboarding", vars, "ai");
        } catch (Exception e) {
            log.warn("온보딩 AI 추천 실패, 규칙 기반 폴백: {}", e.getMessage());
            tempResult = ruleBasedOnboarding(profile, difficulty);
        }
        final RecommendationFilterDto result = tempResult;

        // ← 추가: users 테이블에 저장
        userRepository.findById(profile.getUserId()).ifPresent(user -> {
            user.updateOnboarding(
                    profile.getCodingLevel(),
                    profile.isCotePrepared(),
                    result.getDifficulty()
            );
            userRepository.save(user);
        });

        return result;
    }

    // ── 개인화 추천 (기존 유저) ────────────────────────────────────
    public RecommendationFilterDto recommendPersonalized(UserProfileDto profile) {
        if (profile.getTotalSolved() < AI_THRESHOLD) {
            return recommendOnboarding(profile);
        }

        try {
            double correctRate = profile.getTotalSolved() == 0 ? 0.0
                    : (double) profile.getCorrectCount() / profile.getTotalSolved();

            List<Integer> weakTopicIds = getWeakTopicIds(profile.getCategoryStats());
            List<Integer> strongTopicIds = getStrongTopicIds(profile.getCategoryStats());

            Map<String, String> vars = PromptLoader.vars(
                    "level",              String.valueOf(profile.getLevel()),
                    "coding_level_label", codingLevelLabel(profile.getCodingLevel()),
                    "cote_prepared_label", profile.isCotePrepared() ? "있음" : "없음",
                    "preferred_language", profile.getPreferredLanguage() != null
                            ? profile.getPreferredLanguage() : "미설정",
                    "total_solved",       String.valueOf(profile.getTotalSolved()),
                    "correct_rate",       String.format("%.0f%%", correctRate * 100),
                    "avg_time_sec",       String.valueOf(
                            profile.getAvgTimeSpentSec() != null
                                    ? profile.getAvgTimeSpentSec().intValue() : 0),
                    "category_stats",     formatCategoryStats(profile.getCategoryStats()),
                    "weak_topic_ids",     weakTopicIds.isEmpty() ? "없음" : weakTopicIds.toString(),
                    "strong_topic_ids",   strongTopicIds.isEmpty() ? "없음" : strongTopicIds.toString()
            );
            return callGpt("personalized", vars, "ai");
        } catch (Exception e) {
            log.warn("개인화 AI 추천 실패, 규칙 기반 폴백: {}", e.getMessage());
            return ruleBasedPersonalized(profile);
        }
    }

    // ── GPT 호출 ──────────────────────────────────────────────────
    private RecommendationFilterDto callGpt(
            String promptType,
            Map<String, String> vars,
            String method) throws Exception {

        String systemPrompt = promptLoader.getSystemPrompt(promptType);
        String userPrompt   = promptLoader.getUserPrompt(promptType, vars);

        Map<String, Object> body = new HashMap<>();
        body.put("model", GPT_MODEL);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.3);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_URL, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class
        );

        JsonNode root    = objectMapper.readTree(response.getBody());
        JsonNode content = root.path("choices").get(0).path("message").path("content");
        JsonNode result  = objectMapper.readTree(content.asText());

        List<Integer> topicIds = new ArrayList<>();
        result.path("topic_ids").forEach(n -> topicIds.add(n.asInt()));

        return RecommendationFilterDto.builder()
                .difficulty(result.path("difficulty").asText("초급"))
                .topicIds(topicIds)
                .type(result.path("type").asText("객관식"))
                .style(result.path("style").asText("일반"))
                .language(result.path("language").asText("COMMON"))
                .reason(result.path("reason").asText())
                .focusPoint(result.path("focus_point").asText())
                .method(method)
                .build();
    }

    // ── 규칙 기반 폴백 ─────────────────────────────────────────────
    private RecommendationFilterDto ruleBasedOnboarding(
            UserProfileDto profile, String difficulty) {
        return RecommendationFilterDto.builder()
                .difficulty(difficulty)
                .topicIds(List.of(1))
                .type("객관식")
                .style("일반")
                .language(profile.getPreferredLanguage() != null
                        ? profile.getPreferredLanguage() : "COMMON")
                .reason("경험을 바탕으로 " + difficulty + " 문제부터 시작해 보세요!")
                .focusPoint("알고리즘 기초 개념 다지기")
                .method("rule_based")
                .build();
    }

    private RecommendationFilterDto ruleBasedPersonalized(UserProfileDto profile) {
        List<Integer> weakTopicIds = getWeakTopicIds(profile.getCategoryStats());
        List<Integer> topicIds = weakTopicIds.isEmpty() ? List.of(1) : weakTopicIds;

        double correctRate = profile.getTotalSolved() == 0 ? 0.0
                : (double) profile.getCorrectCount() / profile.getTotalSolved();
        String difficulty = correctRate >= 0.8 ? raiseDifficulty(
                scoreToDifficulty(calcOnboardingScore(profile)))
                : scoreToDifficulty(calcOnboardingScore(profile));

        return RecommendationFilterDto.builder()
                .difficulty(difficulty)
                .topicIds(topicIds)
                .type("객관식")
                .style("일반")
                .language(profile.getPreferredLanguage() != null
                        ? profile.getPreferredLanguage() : "COMMON")
                .reason(String.format("정답률 %.0f%%, 꾸준히 풀어나가고 있어요!", correctRate * 100))
                .focusPoint("취약 카테고리 집중 보완")
                .method("rule_based")
                .build();
    }

    // ── 점수 계산 ──────────────────────────────────────────────────
    private int calcOnboardingScore(UserProfileDto profile) {
        int score = switch (profile.getCodingLevel()) {
            case "LOTS" -> 60;
            case "SOME" -> 30;
            default     -> 0;
        };
        if (profile.isCotePrepared()) score += 40;
        return score;
    }

    private String scoreToDifficulty(int score) {
        if (score < 25)  return "입문";
        if (score < 50)  return "초급";
        if (score < 75)  return "중급";
        return "고급";
    }

    private String raiseDifficulty(String current) {
        return switch (current) {
            case "입문" -> "초급";
            case "초급" -> "중급";
            case "중급" -> "고급";
            default     -> current;
        };
    }

    // ── 유틸 ───────────────────────────────────────────────────────
    private List<Integer> getWeakTopicIds(List<CategoryStatDto> stats) {
        if (stats == null) return List.of();
        return stats.stream()
                .filter(s -> s.getTotalSolved() >= 3 && s.getCorrectRate() < 0.5)
                .map(CategoryStatDto::getTopicId)
                .collect(Collectors.toList());
    }

    private List<Integer> getStrongTopicIds(List<CategoryStatDto> stats) {
        if (stats == null) return List.of();
        return stats.stream()
                .filter(s -> s.getTotalSolved() >= 3 && s.getCorrectRate() >= 0.8)
                .map(CategoryStatDto::getTopicId)
                .collect(Collectors.toList());
    }

    private String formatCategoryStats(List<CategoryStatDto> stats) {
        if (stats == null || stats.isEmpty()) return "- 아직 풀이 이력 없음";
        return stats.stream()
                .map(s -> String.format("- topic_id=%d (%s): %.0f%% (%d/%d)",
                        s.getTopicId(), s.getTopicName(),
                        s.getCorrectRate() * 100,
                        s.getCorrectCount(), s.getTotalSolved()))
                .collect(Collectors.joining("\n"));
    }

    private String codingLevelLabel(String level) {
        return switch (level) {
            case "LOTS" -> "많이 해봤어요";
            case "SOME" -> "조금 해봤어요";
            default     -> "처음이에요";
        };
    }
}
