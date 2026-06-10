package com.besenior.harucoding.generation.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 코드 실행 검증 진입점.
 *
 * 생성된(또는 시드의) 문제 항목 1개(JSON)를 받아:
 *   1) Type별로 실행용 코드 조립(CodeComposer)
 *   2) 언어별 Runner로 컴파일/실행
 *   3) stdout과 기대 출력(IO Example.output)을 비교
 *
 * 기대 출력은 Runner가 JSON 직렬화(", " 구분)로 찍으므로, 문자열이 아닌 경우
 * 같은 형식으로 직렬화해 비교한다.
 */
@Service
public class CodeVerifier {

    private final Map<String, Runner> runners = Map.of(
            "Python", new PythonRunner(),
            "Java", new JavaRunner(),
            "C++", new CppRunner()
    );

    /**
     * @param problem  문제 항목 JSON (Type/Signature/IO Example/Code Skeleton/Answer)
     * @param language Python | Java | C++
     */
    public VerifyResult verify(JsonNode problem, String language) {
        Runner runner = runners.get(language);
        if (runner == null) {
            return VerifyResult.fail("internal_error", "지원하지 않는 언어: " + language);
        }
        try {
            String type = problem.path("Type").asText();
            String signature = problem.path("Signature").asText();

            JsonNode io = problem.path("IO Example");
            String ioInput = io.path("input").asText();
            String expected = expectedToString(io.path("output"));

            String codeSkeleton = problem.hasNonNull("Code Skeleton")
                    ? problem.get("Code Skeleton").asText() : null;
            Object answer = parseAnswer(problem.path("Answer"));

            CodeComposer.Composed composed = CodeComposer.compose(type, codeSkeleton, answer);
            if (composed.code() == null) {
                return VerifyResult.fail("internal_error", composed.error());
            }
            return runner.run(composed.code(), ioInput, expected, signature);
        } catch (Exception e) {
            return VerifyResult.fail("internal_error", "검증 준비 실패: " + e.getMessage());
        }
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

    /** output 노드를 Runner 출력과 동일한 JSON(", " 구분) 문자열로 변환. */
    private String expectedToString(JsonNode output) {
        // 모델이 이미 문자열로 적었으면 그대로 사용 (예: "15", "[1, 2, 3]")
        if (output.isTextual()) {
            return output.asText();
        }
        StringBuilder sb = new StringBuilder();
        writePy(sb, output);
        return sb.toString();
    }

    /** Python json.dumps(ensure_ascii=False) 기본 형식과 동일하게 직렬화. */
    private void writePy(StringBuilder sb, JsonNode n) {
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

    private String escape(String s) {
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
