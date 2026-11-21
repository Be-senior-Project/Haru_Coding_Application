package com.besenior.harucoding.generation.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** 예시 + 숨겨진 테스트 채점과 숨겨진 테스트 출력 계산. 실제 실행 대신 정해진 출력을 내는 실행기를 쓴다. */
class CodeVerifierGradeTest {

    private final ObjectMapper om = new ObjectMapper();

    /** 예시: a + b, a=1 b=2 → 3 */
    private JsonNode problem() throws Exception {
        return om.readTree("""
                {"Type": "Implementation",
                 "Signature": "def solution(a, b):",
                 "IO Example": {"input": "a = 1\\nb = 2", "output": "3"},
                 "Code Skeleton": "def solution(a, b):\\n    {{CORE}}",
                 "Answer": "    return a + b"}
                """);
    }

    private static final List<Map<String, String>> HIDDEN = List.of(
            Map.of("input", "a = 5\nb = 7", "output", "12"),
            Map.of("input", "a = -1\nb = 1", "output", "0"));

    /** 입력 목록 → 출력 목록을 정해서 돌려주는 실행기. 받은 입력도 기록한다. */
    private static class FakeRunner implements Runner {
        final List<List<String>> calls = new ArrayList<>();
        final Function<List<String>, ExecutionResult> behavior;

        FakeRunner(Function<List<String>, ExecutionResult> behavior) {
            this.behavior = behavior;
        }

        @Override
        public ExecutionResult execute(String answerCode, List<String> ioInputs, String signature) {
            calls.add(ioInputs);
            return behavior.apply(ioInputs);
        }
    }

    private CodeVerifier verifier(FakeRunner runner) {
        return new CodeVerifier(Map.of("Python", runner));
    }

    @Test
    void 예시와_숨겨진_테스트를_모두_맞히면_정답이다() throws Exception {
        FakeRunner runner = new FakeRunner(in -> ExecutionResult.success(List.of("3", "12", "0")));

        GradeResult r = verifier(runner).grade(problem(), "Python", HIDDEN);

        assertThat(r.ok()).isTrue();
        assertThat(r.passed()).isEqualTo(3);
        assertThat(r.total()).isEqualTo(3);
        // 예시와 숨겨진 테스트를 한 번에 실행한다
        assertThat(runner.calls).hasSize(1);
        assertThat(runner.calls.get(0)).containsExactly("a = 1\nb = 2", "a = 5\nb = 7", "a = -1\nb = 1");
    }

    @Test
    void 예시_출력만_그대로_내는_코드는_숨겨진_테스트에서_오답이_된다() throws Exception {
        FakeRunner runner = new FakeRunner(in -> ExecutionResult.success(List.of("3", "3", "3")));

        GradeResult r = verifier(runner).grade(problem(), "Python", HIDDEN);

        assertThat(r.ok()).isFalse();
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.reason()).isEqualTo("output_mismatch");
        // 숨겨진 테스트의 입력·기대 출력은 결과에 드러나지 않는다
        assertThat(r.detail()).contains("숨겨진 테스트 1번").doesNotContain("12", "a = 5");
    }

    @Test
    void 출력_줄_수가_다르면_오답이다() throws Exception {
        // 사용자 코드가 따로 print해서 줄이 하나 더 생긴 경우
        FakeRunner runner = new FakeRunner(in -> ExecutionResult.success(List.of("debug", "3", "12", "0")));

        GradeResult r = verifier(runner).grade(problem(), "Python", HIDDEN);

        assertThat(r.ok()).isFalse();
        assertThat(r.detail()).contains("출력 줄 수");
    }

    @Test
    void 실행_중_오류면_그_전까지_맞은_수와_사유를_돌려준다() throws Exception {
        FakeRunner runner = new FakeRunner(in ->
                ExecutionResult.fail("runtime_error", "ZeroDivisionError", List.of("3")));

        GradeResult r = verifier(runner).grade(problem(), "Python", HIDDEN);

        assertThat(r.ok()).isFalse();
        assertThat(r.reason()).isEqualTo("runtime_error");
        assertThat(r.passed()).isEqualTo(1);
    }

    @Test
    void 숨겨진_테스트가_없는_예전_문제는_예시로만_채점한다() throws Exception {
        FakeRunner runner = new FakeRunner(in -> ExecutionResult.success(List.of("3")));

        GradeResult r = verifier(runner).grade(problem(), "Python", null);

        assertThat(r.ok()).isTrue();
        assertThat(r.total()).isEqualTo(1);
    }

    @Test
    void 숨겨진_테스트_출력은_정답_코드를_한_번에_실행해_계산한다() throws Exception {
        FakeRunner runner = new FakeRunner(in -> ExecutionResult.success(List.of("12", "0")));

        List<Map<String, String>> tests = verifier(runner)
                .computeOutputs(problem(), "Python", List.of("a = 5\nb = 7", "a = -1\nb = 1"));

        assertThat(tests).containsExactly(
                Map.of("input", "a = 5\nb = 7", "output", "12"),
                Map.of("input", "a = -1\nb = 1", "output", "0"));
        assertThat(runner.calls).hasSize(1);
    }

    @Test
    void 한_번에_실행이_실패하면_입력별로_다시_돌려_성공한_입력만_남긴다() throws Exception {
        FakeRunner runner = new FakeRunner(in -> {
            if (in.size() > 1) {
                return ExecutionResult.fail("runtime_error", "IndexError");
            }
            return in.get(0).contains("bad") ? ExecutionResult.fail("runtime_error", "IndexError")
                    : ExecutionResult.success(List.of("12"));
        });

        List<Map<String, String>> tests = verifier(runner)
                .computeOutputs(problem(), "Python", List.of("a = 5\nb = 7", "a = bad"));

        assertThat(tests).containsExactly(Map.of("input", "a = 5\nb = 7", "output", "12"));
    }
}
