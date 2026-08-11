package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.NestingRequest;
import com.besenior.harucoding.DTO.NestingResponse;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class NestingService {

    public NestingResponse format(NestingRequest request) {
        int maxWidth = request.calcMaxWidth();
        String[] lines = request.getCode().split("\n", -1);
        List<String> result = new ArrayList<>();
        List<NestingResponse.ChangeInfo> changes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int depth = calcDepth(line, request.getIndentSize());
            int indentSize = calcIndentSize(depth, maxWidth);
            String normalized = normalizeLine(line, depth, indentSize, maxWidth);

            if (normalized.length() <= maxWidth) {
                result.add(normalized);
                continue;
            }

            String formatted = tryFormatLine(normalized, maxWidth, indentSize);

            if (!formatted.equals(normalized)) {
                String type = detectType(normalized.stripLeading());
                int indentOffset = countLeadingSpaces(normalized) + getKeywordLength(type);
                changes.add(new NestingResponse.ChangeInfo(i + 1, type, indentOffset));
            }

            result.add(formatted);
        }

        return new NestingResponse("success", String.join("\n", result), changes);
    }

    private String tryFormatLine(String line, int maxWidth, int indentSize) {
        String trimmed = line.stripLeading();
        String lead = line.substring(0, line.length() - trimmed.length());

        // if문
        if (trimmed.matches("if\\s*\\(.*\\)\\s*\\{.*")) {
            return formatIfStatement(line, lead, trimmed, indentSize);
        }

        // for문
        if (trimmed.matches("for\\s*\\(.*\\)\\s*\\{.*")) {
            return formatForStatement(line, lead, trimmed, indentSize);
        }

        // 메서드 시그니처
        if (trimmed.matches("(public|private|protected).*\\(.*,.*\\).*\\{.*")) {
            return formatMethodSignature(line, lead, trimmed);
        }

        // + 연결
        if (trimmed.contains("+") && !trimmed.contains("+=")) {
            String concat = formatConcatenation(line, lead, trimmed);
            if (!concat.equals(line)) return concat;
        }

        // while문
        if (trimmed.matches("while\\s*\\(.*\\)\\s*\\{.*")) {
            return formatWhileStatement(line, lead, trimmed, indentSize);
        }

        // 삼항 연산자
        if (trimmed.contains("?") && trimmed.contains(":")) {
            String ternary = formatTernary(line, lead, trimmed);
            if (!ternary.equals(line)) return ternary;
        }

        return line;
    }

    private String formatIfStatement(String original, String lead, String trimmed, int indentSize) {
        int openParen = trimmed.indexOf('(');
        if (openParen < 0) return original;

        int closeParen = findMatchingCloseParen(trimmed, openParen);
        if (closeParen < 0) return original;

        String condition = trimmed.substring(openParen + 1, closeParen);
        String suffix = trimmed.substring(closeParen + 1).stripLeading();

        List<String> parts = splitAtLogicalOperators(condition);
        if (parts.size() <= 1) return original;

        String keyword = trimmed.substring(0, openParen).stripTrailing();
        String alignPad = lead + " ".repeat(indentSize);

        List<String> formatted = new ArrayList<>();
        formatted.add(lead + keyword + " (" + parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            formatted.add(alignPad + parts.get(i));
        }

        String last = formatted.remove(formatted.size() - 1);
        formatted.add(last + ") " + suffix);

        return String.join("\n", formatted);
    }

    private String formatForStatement(String original, String lead, String trimmed, int indentSize) {
        int openParen = trimmed.indexOf('(');
        if (openParen < 0) return original;

        int closeParen = findMatchingCloseParen(trimmed, openParen);
        if (closeParen < 0) return original;

        String inner = trimmed.substring(openParen + 1, closeParen);
        String suffix = trimmed.substring(closeParen + 1).stripLeading();

        List<String> segs = splitAtSemicolons(inner);
        if (segs.size() != 3) return original;

        String alignPad = lead + " ".repeat(indentSize);

        return lead + "for (" + segs.get(0).trim() + ";\n" +
                alignPad + segs.get(1).trim() + ";\n" +
                alignPad + segs.get(2).trim() + ") " + suffix;
    }

    private String formatMethodSignature(String original, String lead, String trimmed) {
        int openParen = trimmed.indexOf('(');
        if (openParen < 0) return original;

        int closeParen = findMatchingCloseParen(trimmed, openParen);
        if (closeParen < 0) return original;

        String beforeParen = trimmed.substring(0, openParen + 1);
        String params = trimmed.substring(openParen + 1, closeParen);
        String suffix = trimmed.substring(closeParen + 1).stripLeading();

        List<String> parts = splitAtCommas(params);
        if (parts.size() <= 1) return original;

        String alignPad = lead + " ".repeat(beforeParen.length());

        List<String> formatted = new ArrayList<>();
        formatted.add(lead + beforeParen + parts.get(0) + ",");
        for (int i = 1; i < parts.size() - 1; i++) {
            formatted.add(alignPad + parts.get(i) + ",");
        }
        formatted.add(alignPad + parts.get(parts.size() - 1) + ") " + suffix);

        return String.join("\n", formatted);
    }

    private List<String> splitAtLogicalOperators(String condition) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;

        while (i < condition.length()) {
            char ch = condition.charAt(i);
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;

            if (depth == 0 && i + 1 < condition.length()) {
                String two = condition.substring(i, i + 2);
                if (two.equals("&&") || two.equals("||")) {
                    parts.add(current.toString().stripTrailing());
                    current = new StringBuilder(two + " ");
                    i += 2;
                    while (i < condition.length() && condition.charAt(i) == ' ') i++;
                    continue;
                }
            }

            current.append(ch);
            i++;
        }

        if (!current.toString().isBlank()) {
            parts.add(current.toString().stripTrailing());
        }

        return parts;
    }

    private List<String> splitAtCommas(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        int angleDepth = 0;

        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == '[') parenDepth++;
            else if (ch == ')' || ch == ']') parenDepth--;
            else if (ch == '<') angleDepth++;
            else if (ch == '>') angleDepth--;

            if (ch == ',' && parenDepth == 0 && angleDepth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }

        if (!current.toString().isBlank()) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    private List<String> splitAtSemicolons(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;

            if (ch == ';' && depth == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }

        if (!current.toString().isBlank()) {
            parts.add(current.toString());
        }

        return parts;
    }

    private int findMatchingCloseParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int calcIndentSize(int depth, int maxWidth) {
        if (maxWidth >= 70) return 4;
        if (maxWidth >= 55) return depth <= 2 ? 4 : 2;
        if (depth <= 1) return 4;
        if (depth == 2) return 3;
        if (depth == 3) return 2;
        return 1;
    }

    private int calcDepth(String line, int baseIndentSize) {
        return countLeadingSpaces(line) / baseIndentSize;
    }

    private String normalizeLine(String line, int depth, int indentSize, int maxWidth) {
        String trimmed = line.stripLeading();
        int totalIndent = 0;
        for (int i = 1; i <= depth; i++) {
            totalIndent += calcIndentSize(i, maxWidth);
        }
        return " ".repeat(totalIndent) + trimmed;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char ch : line.toCharArray()) {
            if (ch == ' ') count++;
            else break;
        }
        return count;
    }

    private String detectType(String trimmed) {
        if (trimmed.startsWith("if")) return "if_wrap";
        if (trimmed.startsWith("for")) return "for_wrap";
        if (trimmed.startsWith("while")) return "while_wrap";
        if (trimmed.matches("(public|private|protected).*")) return "method_wrap";
        return "unknown";
    }

    private int getKeywordLength(String type) {
        return switch (type) {
            case "if_wrap" -> "if (".length();
            case "for_wrap" -> "for (".length();
            case "while_wrap" -> "while (".length();
            default -> 0;
        };
    }

    private String formatConcatenation(String original, String lead, String trimmed) {
        int openParen = trimmed.indexOf('(');
        if (openParen >= 0) {
            int closeParen = findMatchingCloseParen(trimmed, openParen);
            if (closeParen >= 0) {
                String inner = trimmed.substring(openParen + 1, closeParen);
                List<String> parts = splitAtPlus(inner);

                if (parts.size() > 1) {
                    String beforeParen = trimmed.substring(0, openParen + 1);
                    String suffix = trimmed.substring(closeParen);
                    String alignPad = lead + " ".repeat(beforeParen.length());

                    List<String> formatted = new ArrayList<>();
                    formatted.add(lead + beforeParen + parts.get(0));
                    for (int i = 1; i < parts.size(); i++) {
                        formatted.add(alignPad + parts.get(i));
                    }
                    formatted.set(
                            formatted.size() - 1,
                            formatted.get(formatted.size() - 1) + suffix
                    );

                    return String.join("\n", formatted);
                }
            }
        }

        int eqIdx = trimmed.indexOf('=');
        if (eqIdx >= 0
                && eqIdx < trimmed.length() - 1
                && trimmed.charAt(eqIdx - 1) != '!'
                && trimmed.charAt(eqIdx - 1) != '<'
                && trimmed.charAt(eqIdx - 1) != '>'
                && trimmed.charAt(eqIdx + 1) != '=') {

            String beforeEq = trimmed.substring(0, eqIdx + 2);
            String afterEq = trimmed.substring(eqIdx + 2).stripLeading();

            List<String> parts = splitAtPlus(afterEq);
            if (parts.size() > 1) {
                String alignPad = lead + " ".repeat(beforeEq.length());

                List<String> formatted = new ArrayList<>();
                formatted.add(lead + beforeEq + parts.get(0));
                for (int i = 1; i < parts.size(); i++) {
                    formatted.add(alignPad + parts.get(i));
                }
                return String.join("\n", formatted);
            }
        }

        return original;
    }

    private List<String> splitAtPlus(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;

        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;

            if (ch == '+' && depth == 0) {
                if (i + 1 < s.length() && s.charAt(i + 1) == '+') {
                    current.append("++");
                    i += 2;
                    continue;
                }
                if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                    current.append("+=");
                    i += 2;
                    continue;
                }
                parts.add(current.toString().stripTrailing());
                current = new StringBuilder("+ ");
                i++;
                while (i < s.length() && s.charAt(i) == ' ') i++;
                continue;
            }

            current.append(ch);
            i++;
        }

        if (!current.toString().isBlank()) {
            parts.add(current.toString().stripTrailing());
        }

        return parts;
    }

    private String formatWhileStatement(String original, String lead, String trimmed, int indentSize) {
        int openParen = trimmed.indexOf('(');
        if (openParen < 0) return original;

        int closeParen = findMatchingCloseParen(trimmed, openParen);
        if (closeParen < 0) return original;

        String condition = trimmed.substring(openParen + 1, closeParen);
        String suffix = trimmed.substring(closeParen + 1).stripLeading();

        List<String> parts = splitAtLogicalOperators(condition);
        if (parts.size() <= 1) return original;

        String alignPad = lead + " ".repeat(indentSize);

        List<String> formatted = new ArrayList<>();
        formatted.add(lead + "while (" + parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            formatted.add(alignPad + parts.get(i));
        }

        String last = formatted.remove(formatted.size() - 1);
        formatted.add(last + ") " + suffix);

        return String.join("\n", formatted);
    }

    private String formatTernary(String original, String lead, String trimmed) {
        int eqIdx = trimmed.indexOf('=');
        if (eqIdx < 0 || trimmed.charAt(eqIdx + 1) == '=') return original;

        int questionIdx = -1;
        int colonIdx = -1;
        int depth = 0;

        for (int i = eqIdx; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;

            if (depth == 0 && ch == '?' && questionIdx < 0) {
                questionIdx = i;
            }
            if (depth == 0 && ch == ':' && questionIdx >= 0) {
                colonIdx = i;
                break;
            }
        }

        if (questionIdx < 0 || colonIdx < 0) return original;

        String beforeQuestion = trimmed.substring(0, questionIdx).stripTrailing();
        String truePart = trimmed.substring(questionIdx + 1, colonIdx).trim();
        String falsePart = trimmed.substring(colonIdx + 1).trim();

        String alignPad = lead + " ".repeat(eqIdx + 2);

        return lead + beforeQuestion + "\n" +
                alignPad + "? " + truePart + "\n" +
                alignPad + ": " + falsePart;
    }
}
