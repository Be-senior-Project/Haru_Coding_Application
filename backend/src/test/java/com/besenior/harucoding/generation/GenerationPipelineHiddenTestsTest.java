package com.besenior.harucoding.generation;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.VerifyResult;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 문제 생성 시 숨겨진 테스트 입력을 정리하고, 정답 코드로 계산한 출력과 함께 저장하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class GenerationPipelineHiddenTestsTest {

    @Mock private SeedRetriever seedRetriever;
    @Mock private ProblemGenerationClient generationClient;
    @Mock private CodeVerifier codeVerifier;
    @Mock private ProblemEmbeddingService embeddingService;
    @Mock private ProblemRepository problemRepository;

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode generatedSet() throws Exception {
        return om.readTree("""
                {"Set": {"Set Id": "SET-1", "Concept Explanation": "개념"},
                 "Problems": [{
                   "Type": "Implementation", "Title": "두 수의 합", "Description": "설명",
                   "Signature": "def solution(a, b):",
                   "IO Example": {"input": "a = 1\\nb = 2", "output": "3"},
                   "Hidden Inputs": ["a = 1\\nb = 2", "a = 5\\nb = 7", "a = 5\\nb = 7", "", "a = -1\\nb = 1"],
                   "Code Skeleton": "def solution(a, b):\\n    {{CORE}}",
                   "Answer": "    return a + b",
                   "Explanation": "해설"}]}
                """);
    }

    @Test
    void 숨겨진_입력에서_예시와_같은_입력_중복_빈값을_버린다() throws Exception {
        JsonNode problem = generatedSet().path("Problems").get(0);

        assertThat(GenerationPipeline.hiddenInputs(problem)).containsExactly("a = 5\nb = 7", "a = -1\nb = 1");
    }

    @Test
    void 숨겨진_입력은_최대_5개까지만_쓴다() throws Exception {
        JsonNode problem = om.readTree("""
                {"IO Example": {"input": "n = 0"},
                 "Hidden Inputs": ["n = 1", "n = 2", "n = 3", "n = 4", "n = 5", "n = 6", "n = 7"]}
                """);

        assertThat(GenerationPipeline.hiddenInputs(problem)).hasSize(GenerationPipeline.MAX_HIDDEN_TESTS);
    }

    @Test
    void 정답_코드로_계산한_숨겨진_테스트를_문제와_함께_저장한다() throws Exception {
        List<Map<String, String>> computed = List.of(
                Map.of("input", "a = 5\nb = 7", "output", "12"),
                Map.of("input", "a = -1\nb = 1", "output", "0"));
        when(seedRetriever.buildFewShotExamples(anyString(), any(), anyInt(), anyString())).thenReturn("");
        when(generationClient.generateSet(anyString(), any(), anyString(), anyString(), any(), anyInt(), anyString()))
                .thenReturn(generatedSet());
        when(codeVerifier.verify(any(), eq("Python"))).thenReturn(VerifyResult.pass("3"));
        when(codeVerifier.computeOutputs(any(), eq("Python"), eq(List.of("a = 5\nb = 7", "a = -1\nb = 1"))))
                .thenReturn(computed);
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerationPipeline pipeline = new GenerationPipeline(
                seedRetriever, generationClient, codeVerifier, embeddingService, problemRepository, om);
        pipeline.generateAndSave("Basic/Introductory", null, 0, "Python", List.of("Implementation"), 1);

        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).save(saved.capture());
        assertThat(saved.getValue().getHiddenTests()).isEqualTo(computed);
    }
}
