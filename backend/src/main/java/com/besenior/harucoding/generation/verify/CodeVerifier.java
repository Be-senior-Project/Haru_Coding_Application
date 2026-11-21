package com.besenior.harucoding.generation.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코드 실행 검증·채점 진입점.
 *
 * 문제 항목 1개(JSON)를 받아:
 *   1) Type별로 실행용 코드 조립(CodeComposer)
 *   2) 언어별 Runner로 컴파일/실행 (여러 테스트 입력을 한 번에)
 *   3) 입력별 출력과 기대 출력을 비교
 *
 * 기대 출력은 Runner가 JSON 직렬화(", " 구분)로 찍으므로, 문자열이 아닌 경우
 * 같은 형식으로 직렬화해 비교한다.
 */
@Service
public class CodeVerifier {

    private final Map<String, Runner> runners;

    public CodeVerifier() {
        this(Map.of(
                "Python", new PythonRunner(),
                "Java", new JavaRunner(),
                "C++", new CppRunner()
        ));
    }

    CodeVerifier(Map<String, Runner> runners) {
        this.runners = runners;
    }

    /** 실행에 필요한 재료(실행기, 조립된 코드, 예시). 조립에 실패하면 error에 사유가 있다. */
    private record Prepared(Runner runner, String code, String signature,
                            String exampleInput, String exampleExpected, String error) {
        static Prepared failed(String error) {
            return new Prepared(null, null, null, null, null, error);
        }
    }

    /**
     * 예시 입출력 1개로 검증한다. 생성된 문제의 정답 코드 검증과 '실행' 버튼(예시만 공개)에 쓴다.
     *
     * @param problem  문제 항목 JSON (Type/Signature/IO Example/Code Skeleton/Answer)
     * @param language Python | Java | C++
     */
    public VerifyResult verify(JsonNode problem, String language) {
        Prepared p = prepare(problem, language);
        if (p.error() != null) {
            return VerifyResult.fail("internal_error", p.error());
        }
        ExecutionResult r = p.runner().execute(p.code(), List.of(p.exampleInput()), p.signature());
        if (!r.ok()) {
            return VerifyResult.fail(r.reason(), r.detail());
        }
        String actual = String.join("\n", r.outputs());
        String expected = p.exampleExpected().strip();
        if (!actual.equals(expected)) {
            return VerifyResult.fail("output_mismatch", "expected: " + expected + ", actual: " + actual, actual);
        }
        return VerifyResult.pass(actual);
    }

    /**
     * 예시 + 숨겨진 테스트로 채점한다. 모두 맞아야 ok.
     * 예시 출력만 그대로 출력하는 코드는 숨겨진 테스트에서 걸린다.
     * 숨겨진 테스트의 입력·기대 출력은 결과(detail)에 넣지 않는다.
     *
     * @param hiddenTests [{"input": 변수 선언문, "output": 기대 출력}] (null이면 예시로만 채점)
     */
    public GradeResult grade(JsonNode problem, String language, List<Map<String, String>> hiddenTests) {
        Prepared p = prepare(problem, language);
        if (p.error() != null) {
            return GradeResult.internalError(p.error());
        }

        List<String> inputs = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        inputs.add(p.exampleInput());
        expected.add(p.exampleExpected().strip());
        if (hiddenTests != null) {
            for (Map<String, String> t : hiddenTests) {
                if (t != null && t.get("input") != null && t.get("output") != null) {
                    inputs.add(t.get("input"));
                    expected.add(t.get("output").strip());
                }
            }
        }
        int total = inputs.size();

        ExecutionResult r = p.runner().execute(p.code(), inputs, p.signature());
        List<String> outputs = r.outputs();
        int passed = countPassed(expected, outputs);
        if (r.ok() && passed == total && outputs.size() == total) {
            return new GradeResult(true, total, total, null, null);
        }
        if (!r.ok() && !"runtime_error".equals(r.reason())) {
            // 컴파일 오류·시간 초과 등: 어느 테스트에서 났는지 알 수 없다. 컴파일 오류 메시지는 입력과 무관해 보여 준다.
            String message = "compile_error".equals(r.reason()) ? clip(r.detail()) : null;
            return new GradeResult(false, passed, total, r.reason(), r.detail(), null, message, null, null);
        }
        if (r.ok() && outputs.size() != total) {
            String message = "출력 줄 수(" + outputs.size() + ")가 테스트 수(" + total
                    + ")와 다릅니다. solution 안에서 print 등으로 출력하면 안 됩니다.";
            return new GradeResult(false, passed, total, "output_mismatch", message, null, message, null, null);
        }

        int failed = firstFailed(expected, outputs);
        if (failed < 0 || failed >= outputs.size()) {
            // 출력이 나오기 전에 멈춤 = 그 테스트를 실행하다 오류. 실행기는 테스트마다 한 줄씩 바로 내보내므로
            // 나온 출력 수가 곧 멈춘 테스트 번호다(모든 출력이 맞은 뒤에 난 오류면 번호를 알 수 없다).
            // 숨겨진 테스트에서 난 오류 메시지는 입력값이 드러날 수 있어 보내지 않는다.
            Integer at = failed < 0 ? null : failed;
            String message = at != null && at == 0 ? clip(r.detail()) : null;
            return new GradeResult(false, passed, total, "runtime_error", r.detail(), at, message, null, null);
        }
        if (failed == 0) {
            return new GradeResult(false, passed, total, "output_mismatch",
                    "expected: " + expected.get(0) + ", actual: " + outputs.get(0),
                    0, null, expected.get(0), outputs.get(0));
        }
        return new GradeResult(false, passed, total, "output_mismatch",
                "숨겨진 테스트 " + failed + "번의 출력이 다릅니다.", failed, null, null, null);
    }

    /** 앱에 보여 줄 오류 메시지의 최대 길이. */
    static final int MAX_MESSAGE_CHARS = 1000;

    private static String clip(String s) {
        if (s == null || s.length() <= MAX_MESSAGE_CHARS) return s;
        return s.substring(0, MAX_MESSAGE_CHARS) + "…";
    }

    /**
     * 정답 코드(Answer)로 입력들의 기대 출력을 계산한다(숨겨진 테스트 생성용).
     * AI가 쓴 출력은 틀릴 수 있어 쓰지 않고, 검증을 통과한 정답 코드를 실제로 실행한 결과를 쓴다.
     * 한 번에 실행해 실패하면 입력별로 다시 실행해서, 정답 코드가 정상 실행된 입력만 돌려준다.
     */
    public List<Map<String, String>> computeOutputs(JsonNode problem, String language, List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Prepared p = prepare(problem, language);
        if (p.error() != null) {
            return List.of();
        }

        ExecutionResult all = p.runner().execute(p.code(), inputs, p.signature());
        if (all.ok() && all.outputs().size() == inputs.size()) {
            List<Map<String, String>> tests = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                tests.add(test(inputs.get(i), all.outputs().get(i)));
            }
            return tests;
        }

        List<Map<String, String>> tests = new ArrayList<>();
        for (String input : inputs) {
            ExecutionResult one = p.runner().execute(p.code(), List.of(input), p.signature());
            if (one.ok() && one.outputs().size() == 1) {
                tests.add(test(input, one.outputs().get(0)));
            }
        }
        return tests;
    }

    private static Map<String, String> test(String input, String output) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("input", input);
        t.put("output", output);
        return t;
    }

    private Prepared prepare(JsonNode problem, String language) {
        Runner runner = runners.get(language);
        if (runner == null) {
            return Prepared.failed("지원하지 않는 언어: " + language);
        }
        try {
            String type = problem.path("Type").asText();
            String signature = problem.path("Signature").asText();

            JsonNode io = problem.path("IO Example");
            String ioInput = io.path("input").asText();
            String expected = outputText(io.path("output"));

            String codeSkeleton = problem.hasNonNull("Code Skeleton")
                    ? problem.get("Code Skeleton").asText() : null;
            Object answer = parseAnswer(problem.path("Answer"));

            CodeComposer.Composed composed = CodeComposer.compose(type, codeSkeleton, answer);
            if (composed.code() == null) {
                return Prepared.failed(composed.error());
            }
            return new Prepared(runner, composed.code(), signature, ioInput, expected, null);
        } catch (Exception e) {
            return Prepared.failed("검증 준비 실패: " + e.getMessage());
        }
    }

    /** 앞에서부터 기대 출력과 같은 줄 수. 출력이 모자라면(중간 실패) 그만큼 통과하지 못한 것으로 본다. */
    static int countPassed(List<String> expected, List<String> outputs) {
        int passed = 0;
        for (int i = 0; i < expected.size() && i < outputs.size(); i++) {
            if (outputs.get(i).equals(expected.get(i))) {
                passed++;
            }
        }
        return passed;
    }

    /** 처음 틀린 테스트 번호(0 = 예시). 출력이 모자라면 실행이 멈춘 자리. 모두 맞으면 -1. */
    static int firstFailed(List<String> expected, List<String> outputs) {
        for (int i = 0; i < expected.size(); i++) {
            if (i >= outputs.size() || !outputs.get(i).equals(expected.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Answer: 배열이면 List<String>(빈칸), 아니면 String. */
    private Object parseAnswer(JsonNode answer) {
        if (answer.isArray()) {
            List<String> list = new ArrayList<>();
            answer.forEach(n -> list.add(n.asText()));
            return list;
        }
        return answer.asText();
    }

    /**
     * output 노드를 Runner 출력과 동일한 JSON(", " 구분) 문자열로 변환.
     * 문제를 저장할 때도 이 형식을 써야 채점 때 비교가 맞는다(GenerationPipeline).
     */
    public static String outputText(JsonNode output) {
        // 모델이 이미 문자열로 적었으면 그대로 사용 (예: "15", "[1, 2, 3]")
        if (output.isTextual()) {
            return output.asText();
        }
        StringBuilder sb = new StringBuilder();
        writePy(sb, output);
        return sb.toString();
    }

    /** Python json.dumps(ensure_ascii=False) 기본 형식과 동일하게 직렬화. */
    private static void writePy(StringBuilder sb, JsonNode n) {
        if (n.isNull() || n.isMissingNode()) {
            sb.append("null");
        } else if (n.isTextual()) {
            sb.append('"').append(escape(n.asText())).append('"');
        } else if (n.isBoolean()) {
            sb.append(n.asBoolean() ? "true" : "false");
        } else if (n.isNumber()) {
            sb.append(n.asText());
        } else if (n.isArray()) {
            sb.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) sb.append(", ");
                writePy(sb, n.get(i));
            }
            sb.append(']');
        } else if (n.isObject()) {
            sb.append('{');
            boolean first = true;
            var it = n.fields();
            while (it.hasNext()) {
                var e = it.next();
                if (!first) sb.append(", ");
                first = false;
                sb.append('"').append(escape(e.getKey())).append('"').append(": ");
                writePy(sb, e.getValue());
            }
            sb.append('}');
        } else {
            sb.append('"').append(escape(n.asText())).append('"');
        }
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
