package com.besenior.harucoding.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 사용자 언어 설정 → 문제 언어. 고르지 않았거나 문제가 없는 언어면 Python. */
class LanguageRulesTest {

    @Test
    void 언어_설정을_문제_언어로_바꾼다() {
        assertThat(LanguageRules.problemLanguage("JAVA")).isEqualTo("Java");
        assertThat(LanguageRules.problemLanguage("C")).isEqualTo("C++");
        assertThat(LanguageRules.problemLanguage("PYTHON")).isEqualTo("Python");
    }

    @Test
    void 언어를_고르지_않았거나_문제가_없는_언어면_Python() {
        assertThat(LanguageRules.problemLanguage(null)).isEqualTo("Python");
        assertThat(LanguageRules.problemLanguage("JS")).isEqualTo("Python");
    }
}
