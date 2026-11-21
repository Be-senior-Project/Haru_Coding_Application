package com.besenior.harucoding.global.util;

import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;

import java.util.List;

/**
 * 제출 답안 크기 제한.
 * 답안은 그대로 코드로 조립·실행되고 풀이 기록(jsonb)으로 저장되므로, 지나치게 큰 답안 하나로
 * 서버 메모리와 DB 저장 공간을 크게 쓸 수 있다. 문제 풀이에 필요한 크기는 넉넉히 허용한다.
 */
public final class AnswerLimits {

    public static final int MAX_CODE_CHARS = 20_000;
    public static final int MAX_BLANKS = 20;
    public static final int MAX_BLANK_CHARS = 500;
    /** 세트 제출 한 번에 담을 수 있는 답안 수. */
    public static final int MAX_SET_ANSWERS = 50;

    private AnswerLimits() {}

    /** 답안이 한도를 넘으면 400(ANSWER_TOO_LARGE). 코드 답안은 문자열, 빈칸 답안은 문자열 목록이다. */
    public static void check(Object answer) {
        if (answer == null) {
            return;
        }
        if (answer instanceof String code) {
            if (code.length() > MAX_CODE_CHARS) {
                throw new CustomException(ErrorCode.ANSWER_TOO_LARGE);
            }
            return;
        }
        if (answer instanceof List<?> blanks) {
            if (blanks.size() > MAX_BLANKS) {
                throw new CustomException(ErrorCode.ANSWER_TOO_LARGE);
            }
            for (Object blank : blanks) {
                if (blank != null && String.valueOf(blank).length() > MAX_BLANK_CHARS) {
                    throw new CustomException(ErrorCode.ANSWER_TOO_LARGE);
                }
            }
            return;
        }
        // 그 밖의 형태(JSON 객체 등)도 문자열 길이로 따져 지나치게 큰 값은 막는다
        if (String.valueOf(answer).length() > MAX_CODE_CHARS) {
            throw new CustomException(ErrorCode.ANSWER_TOO_LARGE);
        }
    }

    /** 세트 제출: 답안 수와 각 답안 크기를 확인한다. */
    public static void checkAll(List<?> answers) {
        if (answers != null && answers.size() > MAX_SET_ANSWERS) {
            throw new CustomException(ErrorCode.ANSWER_TOO_LARGE);
        }
    }
}
