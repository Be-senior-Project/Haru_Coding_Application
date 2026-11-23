package com.besenior.harucoding.generation.verify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** runner 공통 유틸: 템플릿 로드, 본문 들여쓰기, 출력 줄 분리, 임시 디렉터리 정리. */
final class RunnerSupport {

    private RunnerSupport() {}

    /** stdout을 테스트별 출력 줄로 나눈다. 줄 끝 공백·CR(Windows)은 지우고, 끝의 빈 줄은 버린다. */
    static List<String> outputLines(String stdout) {
        List<String> lines = new ArrayList<>();
        for (String line : stdout.split("\\R", -1)) {
            lines.add(line.strip());
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * 템플릿에서 처음 나오는 표식 하나만 바꾼다.
     * 사용자 코드를 넣은 뒤에 다른 표식을 바꾸면, 사용자 코드 안에 같은 표식 문자열이 있을 때 그것까지 바뀌어
     * 실행 틀이 조작될 수 있으므로 사용자 코드는 항상 마지막에 이 방법으로 넣는다.
     */
    static String replaceOnce(String template, String marker, String value) {
        int idx = template.indexOf(marker);
        if (idx < 0) {
            throw new IllegalStateException("템플릿에 표식이 없습니다: " + marker);
        }
        return template.substring(0, idx) + value + template.substring(idx + marker.length());
    }

    /** classpath 리소스(템플릿)를 문자열로 로드. */
    static String loadTemplate(String resourcePath) {
        try (InputStream is = RunnerSupport.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("템플릿을 찾을 수 없습니다: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("템플릿 로드 실패: " + resourcePath, e);
        }
    }

    /**
     * 첫 줄은 그대로 두고(템플릿이 이미 들여쓰기를 제공) 둘째 줄부터 spaces만큼 들여쓴다.
     * 빈 줄은 그대로 둔다. (Python _indent(...).lstrip() 과 동일한 효과)
     */
    static String indentBody(String text, int spaces) {
        String pad = " ".repeat(spaces);
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                sb.append(lines[i]);
            } else {
                sb.append("\n");
                if (!lines[i].isEmpty()) sb.append(pad).append(lines[i]);
            }
        }
        return sb.toString();
    }

    /** 임시 디렉터리 재귀 삭제(실패 무시). */
    static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    static String blankToDefault(String s, String def) {
        if (s == null) return def;
        String t = s.strip();
        return t.isEmpty() ? def : t;
    }
}
