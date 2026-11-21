package com.besenior.harucoding.generation.verify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 여러 테스트 입력을 한 번에 실행하는 실행 틀 조립과 출력 줄 분리. */
class RunnerProgramTest {

    @Test
    void 파이썬은_입력마다_선언과_출력을_차례로_붙인다() {
        String program = PythonRunner.buildProgram(
                "def solution(a, b):\n    return a + b", List.of("a = 1\nb = 2", "a = 5\nb = 7"), "a, b");

        int first = program.indexOf("a = 1\nb = 2");
        int second = program.indexOf("a = 5\nb = 7");
        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        assertThat(program.split("print\\(json\\.dumps\\(solution\\(a, b\\)", -1)).hasSize(3);
    }

    @Test
    void 자바는_입력마다_별도_블록을_만들어_같은_변수_이름을_다시_선언할_수_있다() {
        String program = JavaRunner.buildProgram(RunnerSupport.loadTemplate("runner/java_template.txt"),
                "public int solution(int n) { return n * 2; }", List.of("int n = 1;", "int n = 2;"), "n");

        assertThat(program).doesNotContain("__CASES__", "__ANSWER__");
        assertThat(program.split("new Main\\(\\)\\.solution\\(n\\)", -1)).hasSize(3);
        assertThat(program).contains("{\n            int n = 1;", "{\n            int n = 2;");
    }

    @Test
    void Cpp도_입력마다_별도_블록을_만든다() {
        String program = CppRunner.buildProgram(RunnerSupport.loadTemplate("runner/cpp_template.txt"),
                "int solution(int n) { return n * 2; }", List.of("int n = 1;", "int n = 2;"), "n");

        assertThat(program).doesNotContain("__CASES__", "__ANSWER__");
        assertThat(program.split("cout << __json\\(solution\\(n\\)\\)", -1)).hasSize(3);
    }

    @Test
    void 사용자_코드_안의_표식_문자열은_바뀌지_않는다() {
        // 사용자 코드에 표식을 넣어 실행 틀을 조작하려는 시도
        String answer = "public int solution(int n) { String s = \"__CASES__ __ANSWER__\"; return n; }";

        String program = JavaRunner.buildProgram(RunnerSupport.loadTemplate("runner/java_template.txt"),
                answer, List.of("int n = 1;"), "n");

        assertThat(program).contains("\"__CASES__ __ANSWER__\"");
        assertThat(program.split("new Main\\(\\)\\.solution\\(n\\)", -1)).hasSize(2);
    }

    @Test
    void 출력은_줄별로_나누고_줄끝_공백과_마지막_빈_줄을_지운다() {
        assertThat(RunnerSupport.outputLines("3\r\n[1, 2] \n\"a\"\n\n")).containsExactly("3", "[1, 2]", "\"a\"");
        assertThat(RunnerSupport.outputLines("")).isEmpty();
    }
}
