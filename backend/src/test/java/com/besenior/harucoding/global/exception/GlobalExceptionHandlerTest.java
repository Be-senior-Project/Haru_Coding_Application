package com.besenior.harucoding.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 처리되지 않은 예외가 SecurityConfig의 anyRequest().authenticated()로 새어나가
 * 실제 원인과 무관한 403으로 보이던 문제(회원가입 검증 실패 때와 동일한 패턴)의 회귀 테스트.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 커스텀_예외는_정의된_상태코드와_메시지로_응답한다() {
        ResponseEntity<?> res = handler.handleCustomException(new CustomException(ErrorCode.USER_NOT_FOUND));

        assertThat(res.getStatusCode()).isEqualTo(ErrorCode.USER_NOT_FOUND.getStatus());
    }

    @Test
    void JSON_파싱_실패는_403이_아니라_400으로_응답한다() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException("broken");

        ResponseEntity<?> res = handler.handleNotReadable(e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 예상치_못한_예외도_403이_아니라_500으로_명확히_응답한다() {
        ResponseEntity<?> res = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
