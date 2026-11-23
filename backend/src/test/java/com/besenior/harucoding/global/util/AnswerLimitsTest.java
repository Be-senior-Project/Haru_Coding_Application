package com.besenior.harucoding.global.util;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 제출 답안 크기 제한. */
class AnswerLimitsTest {

    private void assertTooLarge(Executable call) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ANSWER_TOO_LARGE));
    }

    @Test
    void 한도_이내의_코드와_빈칸은_통과한다() {
        assertThatCode(() -> AnswerLimits.check("x".repeat(AnswerLimits.MAX_CODE_CHARS))).doesNotThrowAnyException();
        assertThatCode(() -> AnswerLimits.check(Collections.nCopies(AnswerLimits.MAX_BLANKS, "i + 1")))
                .doesNotThrowAnyException();
        assertThatCode(() -> AnswerLimits.check(null)).doesNotThrowAnyException();
    }

    @Test
    void 코드가_2만자를_넘으면_거부한다() {
        assertTooLarge(() -> AnswerLimits.check("x".repeat(AnswerLimits.MAX_CODE_CHARS + 1)));
    }

    @Test
    void 빈칸이_20개를_넘으면_거부한다() {
        assertTooLarge(() -> AnswerLimits.check(Collections.nCopies(AnswerLimits.MAX_BLANKS + 1, "a")));
    }

    @Test
    void 빈칸_하나가_500자를_넘으면_거부한다() {
        assertTooLarge(() -> AnswerLimits.check(List.of("ok", "y".repeat(AnswerLimits.MAX_BLANK_CHARS + 1))));
    }

    @Test
    void 세트_제출_답안이_50개를_넘으면_거부한다() {
        assertTooLarge(() -> AnswerLimits.checkAll(Collections.nCopies(AnswerLimits.MAX_SET_ANSWERS + 1, "a")));
    }
}
