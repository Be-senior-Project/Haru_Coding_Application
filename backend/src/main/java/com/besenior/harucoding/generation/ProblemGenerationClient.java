package com.besenior.harucoding.generation;

import com.besenior.harucoding.global.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions로 "문제 세트(개념 + N문제)"를 생성하는 클라이언트.
 *
 * 기존 RecommendationService.callGpt 패턴(PromptLoader + RestTemplate + JSON 모드)을 그대로 따른다.
 * 반환은 {Set, Problems} 구조의 JsonNode (검증/저장은 파이프라인에서 처리).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemGenerationClient {

    private final PromptLoader promptLoader;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.generation.model:gpt-4o-mini}")
    private String model;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    /**
     * 문제 세트 1개 생성.
     *
     * @param examplesText retriever가 만든 few-shot 예시 블록 (없으면 빈 문자열)
     * @return {Set, Problems} JsonNode, 실패 시 null
     */
    public JsonNode generateSet(String category, String subcategory, String difficulty,
                                String language, List<String> types, int problemsPerSet,
                                String examplesText) {

        Map<String, String> vars = PromptLoader.vars(
                "category", category,
                "subcategory", subcategory != null ? subcategory : "None",
                "difficulty", difficulty,
                "language", language,
                "types", String.join(", ", types),
                "problemsPerSet", String.valueOf(problemsPerSet),
                "examples", examplesText != null ? examplesText : ""
        );

        String systemPrompt = promptLoader.getSystemPrompt("generation");
        String userPrompt = promptLoader.getUserPrompt("generation", vars);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").get(0).path("message").path("content");
            JsonNode data = objectMapper.readTree(content.asText());

            if (!data.has("Set") || !data.has("Problems")
                    || !data.path("Problems").isArray() || data.path("Problems").isEmpty()) {
                log.warn("생성 응답 구조 이상(Set/Problems 누락): {}", data);
                return null;
            }
            return data;
        } catch (Exception e) {
            log.warn("문제 세트 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}
