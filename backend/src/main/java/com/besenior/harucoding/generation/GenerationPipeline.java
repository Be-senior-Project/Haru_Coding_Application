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
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 문제 생성 파이프라인 (엔진 본체).
 *
 * retrieve(시드 few-shot) → generate(세트, #15) → verify(각 문제, #14)
 *   → 숨겨진 테스트 출력 계산(정답 코드 실행)
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

    /**
     * 재시도는 생성을 시작한 뒤 이 시간 안에서만 한다.
     * OpenAI 호출 한 번은 최대 http.client.read-timeout(기본 60초)까지 걸리므로, 30초가 지난 뒤 다시 시도하면
     * 배포 서버 앞단 Cloudflare의 응답 제한(100초)을 넘겨 앱이 오류(524)를 받을 수 있다.
     */
    @Value("${generation.retry-window:30s}")
    private Duration retryWindow = Duration.ofSeconds(30);

    static final int MAX_HIDDEN_TESTS = 5;
    /** 숨겨진 테스트 기대 출력의 최대 길이. 큰 입력에 긴 목록을 돌려주는 테스트는 저장·비교 부담만 커서 뺀다. */
    static final int MAX_HIDDEN_OUTPUT_CHARS = 1000;

    /**
     * 디버깅 문제 규칙(프로그래머스 PCCE와 같은 "1줄만 수정"): 정답과 버그 코드가 정확히 1줄만 다르고,
     * 버그 코드는 컴파일되며 예시 입력에서 틀린 결과를 내야 한다. 어기면 이유를, 지키면(또는 디버깅 문제가 아니면) null.
     * 예전에는 버그 코드를 실행해 보지 않아, 버그가 출력에 영향이 없으면 고치지 않고 제출해도 정답이 될 수 있었다.
     */
    String debuggingRuleViolation(JsonNode pNode, String language) {
        if (!"debugging".equalsIgnoreCase(pNode.path("Type").asText(""))) {
            return null;
        }
        String skeleton = pNode.path("Code Skeleton").asText("");
        if (differentLines(skeleton, pNode.path("Answer").asText("")) != 1) {
            return "정답과 버그 코드가 정확히 1줄만 다르지 않음";
        }
        ObjectNode buggy = pNode.deepCopy();
        buggy.put("Answer", skeleton);   // 디버깅은 Answer를 그대로 실행하므로, 버그 코드를 넣으면 버그 코드가 실행된다
        VerifyResult r = codeVerifier.verify(buggy, language);
        if (r.ok()) {
            return "버그 코드가 예시 입력에서 맞는 결과를 냄";
        }
        if ("compile_error".equals(r.reason())) {
            return "버그 코드가 컴파일되지 않음(문법 오류가 아닌 논리 오류여야 함)";
        }
        return null;
    }

    /** 두 코드에서 서로 다른 줄의 수. 줄 수가 다르면 -1. 줄 앞뒤 공백과 끝의 빈 줄은 무시한다. */
    static int differentLines(String a, String b) {
        List<String> x = codeLines(a);
        List<String> y = codeLines(b);
        if (x.size() != y.size()) {
            return -1;
        }
        int diff = 0;
        for (int i = 0; i < x.size(); i++) {
            if (!x.get(i).equals(y.get(i))) diff++;
        }
        return diff;
    }

    private static List<String> codeLines(String code) {
        List<String> lines = new ArrayList<>();
        for (String line : code.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(line.strip());
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    static List<Map<String, String>> dropLongOutputs(List<Map<String, String>> tests) {
        return tests.stream()
                .filter(t -> t.get("output") == null || t.get("output").length() <= MAX_HIDDEN_OUTPUT_CHARS)
                .toList();
    }

    /** 숨겨진 테스트 입력 하나의 최대 길이. 지나치게 큰 입력은 채점 시간만 늘린다. */
    static final int MAX_HIDDEN_INPUT_CHARS = 2000;

    /**
     * 문제 세트 1개를 생성·검증·저장하고, 통과한 Problem들을 반환. 실패 시 빈 리스트.
     * 전체를 트랜잭션으로 묶지 않는다. OpenAI 응답·코드 검증(수십 초)을 기다리는 동안 DB 연결을 붙잡지 않도록
     * 문제 저장·임베딩 저장만 각각 짧은 트랜잭션(저장소 기본 트랜잭션)으로 처리한다.
     */
    public List<Problem> generateAndSave(String category, String subcategory, int difficulty,
                                         String language, List<String> types, int problemsPerSet) {

        String examples = seedRetriever.buildFewShotExamples(category, subcategory, difficulty, language);

        long startedAt = System.nanoTime();
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 1 && Duration.ofNanos(System.nanoTime() - startedAt).compareTo(retryWindow) >= 0) {
                log.warn("생성 시작 후 {}초가 지나 더 시도하지 않음(앱 응답 시간 제한)", retryWindow.toSeconds());
                break;
            }
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
                String debugViolation = debuggingRuleViolation(pNode, language);
                if (debugViolation != null) {
                    log.info("[{}/{}] 디버깅 문제 제외: {}", attempt, MAX_RETRIES, debugViolation);
                    continue;
                }
                // 숨겨진 테스트: 출력은 AI가 쓴 값이 아니라 검증을 통과한 정답 코드를 실행해 계산한다
                List<Map<String, String>> hiddenTests = dropLongOutputs(
                        codeVerifier.computeOutputs(pNode, language, hiddenInputs(pNode)));
                if (hiddenTests.isEmpty()) {
                    log.info("[{}/{}] 숨겨진 테스트 없음(입력 누락 또는 정답 코드 실행 실패) → 예시로만 채점됨",
                            attempt, MAX_RETRIES);
                }
                Problem problem = problemRepository.save(
                        toProblem(pNode, category, subcategory, difficulty, language, concept, setId, hiddenTests));
                embeddingService.saveEmbedding(problem);
                saved.add(problem);
            }

            if (!saved.isEmpty()) {
                log.info("[{}/{}] 생성 완료: {}문제 저장", attempt, MAX_RETRIES, saved.size());
                return saved;
            }
            log.info("[{}/{}] 통과 문제 없음, 세트 재생성", attempt, MAX_RETRIES);
        }

        log.warn("문제 생성 실패(최대 {}회 시도, 재시도는 시작 후 {}초 안에서만)", MAX_RETRIES, retryWindow.toSeconds());
        return List.of();
    }

    /**
     * AI가 준 숨겨진 테스트 입력("Hidden Inputs")을 정리한다.
     * 빈 값·예시와 같은 입력·중복·지나치게 긴 입력은 버리고 최대 MAX_HIDDEN_TESTS개만 쓴다.
     */
    static List<String> hiddenInputs(JsonNode p) {
        String example = p.path("IO Example").path("input").asText("").strip();
        Set<String> picked = new LinkedHashSet<>();
        for (JsonNode n : p.path("Hidden Inputs")) {
            if (!n.isTextual()) continue;
            String input = n.asText().strip();
            if (input.isEmpty() || input.length() > MAX_HIDDEN_INPUT_CHARS || input.equals(example)) continue;
            picked.add(input);
            if (picked.size() == MAX_HIDDEN_TESTS) break;
        }
        return new ArrayList<>(picked);
    }

    /** 생성된 문제 JSON 1건 → 평탄화된 Problem 엔티티. */
    private Problem toProblem(JsonNode p, String category, String subcategory, int difficulty,
                              String language, String concept, String setId,
                              List<Map<String, String>> hiddenTests) {
        List<String> constraints = p.path("Constraints").isArray()
                ? objectMapper.convertValue(p.path("Constraints"), new TypeReference<List<String>>() {})
                : List.of();

        Map<String, String> io = new LinkedHashMap<>();
        JsonNode ioNode = p.path("IO Example");
        io.put("input", ioNode.path("input").asText(""));
        // 출력이 문자열이 아니면(배열 등) 실행기 출력과 같은 형식("[1, 2, 3]")으로 저장한다.
        // toString()("[1,2,3]")으로 저장하면 채점 때 비교가 어긋나 정답도 오답 처리된다.
        io.put("output", CodeVerifier.outputText(ioNode.path("output")));

        // answer는 jsonb. 배열(빈칸)은 List로, 비배열(코드 문자열)은 JsonNode.toString()으로
        // 따옴표 포함 valid JSON 텍스트로 저장한다. (hypersistence가 String을 raw json으로 취급하므로
        // asText()의 raw 코드 문자열은 jsonb 파싱에 실패함)
        Object answer = p.path("Answer").isArray()
                ? objectMapper.convertValue(p.path("Answer"), new TypeReference<List<String>>() {})
                : p.path("Answer").toString();

        String codeSkeleton = p.hasNonNull("Code Skeleton") ? p.get("Code Skeleton").asText() : null;

        String hint1 = p.hasNonNull("Hint1") ? p.get("Hint1").asText() : null;
        String hint2 = p.hasNonNull("Hint2") ? p.get("Hint2").asText() : null;
        String hint3 = p.hasNonNull("Hint3") ? p.get("Hint3").asText() : null;

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
                .hiddenTests(hiddenTests.isEmpty() ? null : hiddenTests)
                .hint1(hint1)
                .hint2(hint2)
                .hint3(hint3)
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
