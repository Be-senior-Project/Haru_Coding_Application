package com.besenior.harucoding.generation.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signature 문자열에서 파라미터 이름만 순서대로 추출한다.
 * 예) "def solution(n, arr):"                 -> [n, arr]
 *     "public int solution(int n, int[] arr)" -> [n, arr]
 *     "int solution(int n, vector<int>& arr)" -> [n, arr]
 */
public final class SignatureParser {

    private static final Pattern PAREN = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern LAST_WORD = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    private SignatureParser() {}

    public static List<String> extractParamNames(String signature) {
        Matcher m = PAREN.matcher(signature);
        if (!m.find()) {
            throw new IllegalArgumentException("Signature에서 괄호를 찾지 못했습니다: " + signature);
        }
        String inner = m.group(1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : inner.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            Matcher m2 = LAST_WORD.matcher(p);
            if (!m2.find()) {
                throw new IllegalArgumentException("Signature 파라미터 파싱 실패: " + part);
            }
            names.add(m2.group(1));
        }
        return names;
    }
}
