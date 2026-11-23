package com.besenior.harucoding.generation;

import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.VerifyResult;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 생성 문제 검증 규칙: 디버깅 문제는 정확히 1줄만 틀려야 하고(PCCE "1줄만 수정"), 긴 출력의 숨겨진 테스트는 뺀다. */
@ExtendWith(MockitoExtension.class)
class GenerationPipelineDebuggingRuleTest {

    @Mock private SeedRetriever seedRetriever;
    @Mock private ProblemGenerationClient generationClient;
    @Mock private CodeVerifier codeVerifier;
    @Mock private ProblemEmbeddingService embeddingService;
    @Mock private ProblemRepository problemRepository;

    private static final String BUGGY = "def solution(n):\n    total = 0\n    for i in range(n):\n        total += i\n    return total";
    private static final String FIXED = "def solution(n):\n    total = 0\n    for i in range(n + 1):\n        total += i\n    return total";

    private GenerationPipeline pipeline() {
        return new GenerationPipeline(seedRetriever, generationClient, codeVerifier,
                embeddingService, problemRepository, new ObjectMapper());
    }

    private static JsonNode debugging(String skeleton, String answer) {
        return new ObjectMapper().createObjectNode()
                .put("Type", "Debugging").put("Code Skeleton", skeleton).put("Answer", answer);
    }

    @Test
    void 다른_줄_수는_앞뒤_공백과_끝의_빈_줄을_무시하고_센다() {
        assertThat(GenerationPipeline.differentLines(BUGGY, FIXED)).isEqualTo(1);
        assertThat(GenerationPipeline.differentLines(BUGGY, "  " + FIXED.replace("\n", "\n  ") + "\n\n")).isEqualTo(1);
        assertThat(GenerationPipeline.differentLines(BUGGY, FIXED.replace("total = 0", "total = 1"))).isEqualTo(2);
        assertThat(GenerationPipeline.differentLines(BUGGY, FIXED + "\n    print(1)")).isEqualTo(-1);   // 줄 수가 다름
    }

    @Test
    void 버그_코드가_예시에서_틀리면_통과() {
        when(codeVerifier.verify(argThat(n -> BUGGY.equals(n.path("Answer").asText())), eq("Python")))
                .thenReturn(VerifyResult.fail("output_mismatch", "expected: 10, actual: 6"));

        assertThat(pipeline().debuggingRuleViolation(debugging(BUGGY, FIXED), "Python")).isNull();
    }

    @Test
    void 버그_코드가_예시에서_맞는_결과를_내면_제외() {
        when(codeVerifier.verify(any(), eq("Python"))).thenReturn(VerifyResult.pass("10"));

        assertThat(pipeline().debuggingRuleViolation(debugging(BUGGY, FIXED), "Python")).contains("맞는 결과");
    }

    @Test
    void 버그_코드가_컴파일되지_않으면_제외() {
        when(codeVerifier.verify(any(), eq("Python"))).thenReturn(VerifyResult.fail("compile_error", "SyntaxError"));

        assertThat(pipeline().debuggingRuleViolation(debugging(BUGGY, FIXED), "Python")).contains("컴파일");
    }

    @Test
    void 두_줄_이상_다르면_실행하지_않고_제외() {
        String twoLines = FIXED.replace("total = 0", "total = 1");

        assertThat(pipeline().debuggingRuleViolation(debugging(BUGGY, twoLines), "Python")).contains("1줄");
        verifyNoInteractions(codeVerifier);
    }

    @Test
    void 디버깅이_아닌_문제는_검사하지_않는다() {
        JsonNode implementation = new ObjectMapper().createObjectNode().put("Type", "Implementation");

        assertThat(pipeline().debuggingRuleViolation(implementation, "Python")).isNull();
        verifyNoInteractions(codeVerifier);
    }

    @Test
    void 출력이_1000자를_넘는_숨겨진_테스트는_뺀다() {
        List<Map<String, String>> tests = List.of(
                Map.of("input", "n = 1", "output", "1"),
                Map.of("input", "n = 100000", "output", "x".repeat(1001)));

        assertThat(GenerationPipeline.dropLongOutputs(tests)).extracting(t -> t.get("input")).containsExactly("n = 1");
    }
}
