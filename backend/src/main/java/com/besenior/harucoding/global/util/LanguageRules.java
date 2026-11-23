package com.besenior.harucoding.global.util;

/** 사용자 언어 설정과 문제 언어 사이의 변환 규칙. 세트 구성·추천·세트 생성이 같이 쓴다. */
public final class LanguageRules {

    private LanguageRules() {
    }

    /**
     * 사용자 언어 설정(JAVA·PYTHON·C·JS) → 문제 언어(Python·Java·C++).
     * 언어를 고르지 않았거나 문제가 없는 언어(JS)면 Python. 한 세트 안에서 언어가 섞이지 않게 추천도 같은 언어로 고른다.
     */
    public static String problemLanguage(String preferred) {
        if (preferred == null) return "Python";
        return switch (preferred) {
            case "JAVA" -> "Java";
            case "C" -> "C++";
            default -> "Python";
        };
    }
}
