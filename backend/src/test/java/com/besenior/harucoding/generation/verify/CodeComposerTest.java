package com.besenior.harucoding.generation.verify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 구현 문제 코드 조립: 답안 들여쓰기를 {{CORE}} 위치에 맞춰 한 번만 붙이는지 검증한다. */
class CodeComposerTest {

    /** {{CORE}} 앞 줄이 일반 문장인 형태. 들여쓰기가 두 번 붙으면 unexpected indent 오류가 난다. */
    private static final String SKELETON = "def solution(n):\n    total = 0\n    {{CORE}}\n    return total";
    private static final String EXPECTED =
            "def solution(n):\n    total = 0\n    for i in range(n):\n        total += i\n    return total";

    private static String implementation(String skeleton, String answer) {
        CodeComposer.Composed c = CodeComposer.compose("Implementation", skeleton, answer);
        assertThat(c.error()).isNull();
        return c.code();
    }

    @Test
    void 사용자가_0칸부터_쓴_답안을_CORE_위치에_맞춰_한_번만_들여쓴다() {
        assertThat(implementation(SKELETON, "for i in range(n):\n    total += i")).isEqualTo(EXPECTED);
    }

    @Test
    void 절대_들여쓰기로_쓴_정답도_두_번_들여쓰지_않는다() {
        assertThat(implementation(SKELETON, "    for i in range(n):\n        total += i")).isEqualTo(EXPECTED);
    }

    @Test
    void CORE_표식이_0칸이면_앞_코드_줄의_들여쓰기를_따른다() {
        // 시드에 있는 형태: 함수 본문 중간인데 표식만 0칸에 있다
        String skeleton = "def solution(n):\n    if n <= 2:\n        return n\n    a, b = 1, 2\n{{CORE}}";
        assertThat(implementation(skeleton, "for _ in range(3, n + 1):\n    a, b = b, a + b\nreturn b"))
                .isEqualTo("def solution(n):\n    if n <= 2:\n        return n\n    a, b = 1, 2\n"
                        + "    for _ in range(3, n + 1):\n        a, b = b, a + b\n    return b");
    }

    @Test
    void CORE_표식이_0칸이고_앞_줄이_콜론으로_끝나면_한_단계_더_들여쓴다() {
        assertThat(implementation("def solution(arr):\n{{CORE}}", "return sum(arr)"))
                .isEqualTo("def solution(arr):\n    return sum(arr)");
    }

    @Test
    void 탭은_공백_4칸으로_보고_빈_줄은_비워_둔다() {
        assertThat(implementation(SKELETON, "for i in range(n):\n\ttotal += i\n\n"))
                .isEqualTo("def solution(n):\n    total = 0\n    for i in range(n):\n        total += i\n\n\n    return total");
    }

    @Test
    void 같은_줄에_다른_코드가_있는_CORE는_그_자리에_넣는다() {
        assertThat(implementation("def solution(a, b):\n    return {{CORE}}", "a + b"))
                .isEqualTo("def solution(a, b):\n    return a + b");
    }

    @Test
    void Java_답안도_CORE_위치에_맞춰_조립한다() {
        String skeleton = "public int solution(int n) {\n    int s = 0;\n    {{CORE}}\n    return s;\n}";
        assertThat(implementation(skeleton, "for (int i = 0; i < n; i++) {\n    s += i;\n}"))
                .isEqualTo("public int solution(int n) {\n    int s = 0;\n    for (int i = 0; i < n; i++) {\n"
                        + "        s += i;\n    }\n    return s;\n}");
    }
}
