package com.besenior.harucoding.service;

import com.besenior.harucoding.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.besenior.harucoding.DTO.RecommendationFilterDto;
import com.besenior.harucoding.DTO.UserProfileDto;
import com.besenior.harucoding.global.util.PromptLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 온보딩(신규 유저) 추천 전용.
 * - coding_level / cote_prepared 기반 난이도·이유·집중포인트 산출 (GPT + 규칙 폴백)
 * - 기존 유저 개인화 추천은 ProblemRecommendationService(/api/recommendations)로 대체됨
 */
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

    @PostConstruct
    public void init() {
        log.info("OpenAI Key 앞 10자리: {}",
                openaiApiKey != null ? openaiApiKey.substring(0, Math.min(10, openaiApiKey.length())) : "NULL");
    }

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

        // users 테이블에 온보딩 결과 저장
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

    private String codingLevelLabel(String level) {
        return switch (level) {
            case "LOTS" -> "많이 해봤어요";
            case "SOME" -> "조금 해봤어요";
            default     -> "처음이에요";
        };
    }
}
