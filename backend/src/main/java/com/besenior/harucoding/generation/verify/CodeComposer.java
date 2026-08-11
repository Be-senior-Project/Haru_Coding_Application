package com.besenior.harucoding.generation.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type별로 실행용 "완성 코드"를 조립한다.
 *  - Implementation:     Code Skeleton의 {{CORE}} 라인을 Answer(절대 들여쓰기 포함)로 치환
 *  - Debugging:          Answer 그대로 (이미 수정된 완성 코드)
 *  - Fill-in-the-blank:  Code Skeleton의 {{BLANK_N}}을 Answer 리스트로 순서대로 치환
 */
public final class CodeComposer {

    private static final Pattern BLANK = Pattern.compile("\\{\\{BLANK_(\\d+)\\}\\}");
    private static final String CORE = "{{CORE}}";

    private CodeComposer() {}

    /** code가 null이면 실패, error에 사유. */
    public record Composed(String code, String error) {}

    public static Composed compose(String type, String codeSkeleton, Object answer) {
        if ("Implementation".equals(type)) {
            if (!(answer instanceof String ans)) {
                return new Composed(null, "Implementation Type의 Answer는 문자열이어야 합니다.");
            }
            if (codeSkeleton == null || codeSkeleton.isEmpty()) {
                return new Composed(null, "Implementation Type은 Code Skeleton이 필요합니다.");
            }
            int count = countOccurrences(codeSkeleton, CORE);
            if (count != 1) {
                return new Composed(null, "Implementation Type은 {{CORE}} 표식이 정확히 1개여야 합니다. 발견: " + count);
            }
            StringBuilder sb = new StringBuilder();
            String[] lines = codeSkeleton.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.strip().equals(CORE)) {
                    sb.append(ans);
                } else if (line.contains(CORE)) {
                    sb.append(line.replace(CORE, ans));
                } else {
                    sb.append(line);
                }
                if (i < lines.length - 1) sb.append("\n");
            }
            return new Composed(sb.toString(), null);
        }

        if ("Debugging".equals(type)) {
            if (!(answer instanceof String ans)) {
                return new Composed(null, "Debugging Type의 Answer는 문자열이어야 합니다.");
            }
            return new Composed(ans, null);
        }

        if ("Fill-in-the-blank".equals(type)) {
            if (!(answer instanceof List<?> rawList)) {
                return new Composed(null, "Fill-in-the-blank Type의 Answer는 문자열 리스트여야 합니다.");
            }
            List<String> ans = new ArrayList<>();
            for (Object o : rawList) {
                if (!(o instanceof String s)) {
                    return new Composed(null, "Fill-in-the-blank Answer의 각 원소는 문자열이어야 합니다.");
                }
                ans.add(s);
            }
            if (codeSkeleton == null || codeSkeleton.isEmpty()) {
                return new Composed(null, "Fill-in-the-blank Type은 Code Skeleton이 필요합니다.");
            }
            Matcher m = BLANK.matcher(codeSkeleton);
            int n = 0;
            while (m.find()) n++;
            if (n != ans.size()) {
                return new Composed(null, "빈칸 개수 불일치: Code Skeleton " + n + "개, Answer " + ans.size() + "개");
            }
            String composed = codeSkeleton;
            for (int i = 0; i < ans.size(); i++) {
                composed = composed.replace("{{BLANK_" + (i + 1) + "}}", ans.get(i));
            }
            return new Composed(composed, null);
        }

        return new Composed(null, "알 수 없는 Type: " + type);
    }

    private static int countOccurrences(String s, String sub) {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
