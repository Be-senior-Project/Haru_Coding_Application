package com.besenior.harucoding.generation.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type별로 실행용 "완성 코드"를 조립한다.
 *  - Implementation:     Code Skeleton의 {{CORE}} 라인을 Answer로 치환. Answer의 공통 들여쓰기를 걷어낸 뒤
 *                        {{CORE}} 위치의 들여쓰기를 붙인다. 사용자가 0칸부터 쓴 답안과 AI가 절대 들여쓰기로 쓴
 *                        정답을 같은 방식으로 처리한다. (들여쓰기는 여기서만 붙인다. 두 번 붙으면 Python 문법 오류)
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
                    sb.append(indentBlock(ans, coreIndent(lines, i)));
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

    /**
     * {{CORE}} 자리에 붙일 들여쓰기. 표식 줄의 들여쓰기를 그대로 쓰되, 표식이 0칸에 있으면 바로 앞 코드 줄을 기준으로 정한다
     * (앞 줄이 ':'로 끝나면 한 단계 더). 함수 본문 중간인데 표식만 0칸에 둔 스켈레톤이 있어서다.
     */
    static String coreIndent(String[] lines, int coreIdx) {
        String own = lines[coreIdx].substring(0, lines[coreIdx].indexOf(CORE));
        if (!own.isEmpty()) return own;
        for (int k = coreIdx - 1; k >= 0; k--) {
            String prev = expandLeadingTabs(lines[k]);
            if (prev.isBlank()) continue;
            String lead = prev.substring(0, leadingSpaces(prev));
            String code = prev.strip().replaceFirst("\\s+#.*$", "");
            return code.endsWith(":") ? lead + "    " : lead;
        }
        return own;
    }

    /** answer의 공통 들여쓰기를 걷어낸 뒤 모든 줄 앞에 indent를 붙인다. 빈 줄은 비워 두고, 줄 앞 탭은 공백 4칸으로 본다. */
    static String indentBlock(String answer, String indent) {
        String[] ls = answer.replace("\r", "").split("\n", -1);
        int min = Integer.MAX_VALUE;
        for (int j = 0; j < ls.length; j++) {
            ls[j] = expandLeadingTabs(ls[j]);
            if (!ls[j].isBlank()) min = Math.min(min, leadingSpaces(ls[j]));
        }
        if (min == Integer.MAX_VALUE) min = 0;
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < ls.length; j++) {
            if (j > 0) sb.append('\n');
            if (!ls[j].isBlank()) sb.append(indent).append(ls[j].substring(min));
        }
        return sb.toString();
    }

    private static String expandLeadingTabs(String line) {
        int i = 0;
        StringBuilder lead = new StringBuilder();
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            lead.append(line.charAt(i) == '\t' ? "    " : " ");
            i++;
        }
        return lead + line.substring(i);
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
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
