package com.besenior.harucoding.generation;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.VerifyResult;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 문제 생성 파이프라인 (엔진 본체).
 *
 * retrieve(시드 few-shot) → generate(세트, #15) → verify(각 문제, #14)
 *   → 통과분만 평탄화 저장(problems) + 임베딩 저장(problem_embeddings)
 *
 * 검증 정책: 통과 문제가 1개 이상이면 그 통과분으로 저장 후 반환,
 *           전부 실패하면 세트 재생성(최대 MAX_RETRIES).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationPipeline {

    private final SeedRetriever seedRetriever;
    private final ProblemGenerationClient generationClient;
    private final CodeVerifier codeVerifier;
    private final ProblemEmbeddingService embeddingService;
    private final ProblemRepository problemRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;

    /** 문제 세트 1개를 생성·검증·저장하고, 통과한 Problem들을 반환. 실패 시 빈 리스트. */
    @Transactional
    public List<Problem> generateAndSave(String category, String subcategory, int difficulty,
                                         String language, List<String> types, int problemsPerSet) {

        String examples = seedRetriever.buildFewShotExamples(category, subcategory, difficulty, language);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            JsonNode data = generationClient.generateSet(
                    category, subcategory, String.valueOf(difficulty), language, types, problemsPerSet, examples);
            if (data == null) {
                log.info("[{}/{}] 세트 생성 실패(JSON/구조), 재시도", attempt, MAX_RETRIES);
                continue;
            }

            String concept = data.path("Set").path("Concept Explanation").asText("");
            String setId = data.path("Set").path("Set Id").asText(null);

            List<Problem> saved = new ArrayList<>();
            for (JsonNode pNode : data.path("Problems")) {
                VerifyResult vr = codeVerifier.verify(pNode, language);
                if (!vr.ok()) {
                    log.info("[{}/{}] 문제 검증 실패 [{}]: {}", attempt, MAX_RETRIES, vr.reason(), vr.detail());
                    continue;
                }
                Problem problem = problemRepository.save(
                        toProblem(pNode, category, subcategory, difficulty, language, concept, setId));
                embeddingService.saveEmbedding(problem);
                saved.add(problem);
            }

            if (!saved.isEmpty()) {
                log.info("[{}/{}] 생성 완료: {}문제 저장", attempt, MAX_RETRIES, saved.size());
                return saved;
            }
            log.info("[{}/{}] 통과 문제 없음, 세트 재생성", attempt, MAX_RETRIES);
        }

        log.warn("{}회 재시도 후 생성 실패", MAX_RETRIES);
        return List.of();
    }

    /** 생성된 문제 JSON 1건 → 평탄화된 Problem 엔티티. */
    private Problem toProblem(JsonNode p, String category, String subcategory, int difficulty,
                              String language, String concept, String setId) {
        List<String> constraints = p.path("Constraints").isArray()
                ? objectMapper.convertValue(p.path("Constraints"), new TypeReference<List<String>>() {})
                : List.of();

        Map<String, String> io = new LinkedHashMap<>();
        JsonNode ioNode = p.path("IO Example");
        io.put("input", ioNode.path("input").asText(""));
        JsonNode out = ioNode.path("output");
        io.put("output", out.isTextual() ? out.asText() : out.toString());

        // answer는 jsonb. 배열(빈칸)은 List로, 비배열(코드 문자열)은 JsonNode.toString()으로
        // 따옴표 포함 valid JSON 텍스트로 저장한다. (hypersistence가 String을 raw json으로 취급하므로
        // asText()의 raw 코드 문자열은 jsonb 파싱에 실패함)
        Object answer = p.path("Answer").isArray()
                ? objectMapper.convertValue(p.path("Answer"), new TypeReference<List<String>>() {})
                : p.path("Answer").toString();

        String codeSkeleton = p.hasNonNull("Code Skeleton") ? p.get("Code Skeleton").asText() : null;

        return Problem.builder()
                .setId(setId)
                .type(mapType(p.path("Type").asText()))
                .category(category)
                .subcategory(subcategory)
                .difficulty(difficulty)
                .language(language)
                .title(p.path("Title").asText())
                .description(p.path("Description").asText())
                .constraints(constraints)
                .ioExample(io)
                .codeSkeleton(codeSkeleton)
                .answer(answer)
                .explanation(p.path("Explanation").asText(""))
                .conceptExplanation(concept)
                .build();
    }

    private ProblemType mapType(String t) {
        return switch (t) {
            case "Implementation" -> ProblemType.IMPLEMENTATION;
            case "Debugging" -> ProblemType.DEBUGGING;
            case "Fill-in-the-blank" -> ProblemType.FILL_IN_THE_BLANK;
            default -> throw new IllegalArgumentException("알 수 없는 Type: " + t);
        };
    }
}
