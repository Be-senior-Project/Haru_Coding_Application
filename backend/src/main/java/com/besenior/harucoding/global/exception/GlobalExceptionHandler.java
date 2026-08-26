package com.besenior.harucoding.global.exception;

import com.besenior.harucoding.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 여기서 잡지 않는 예외는 Spring이 /error로 넘기는데, /error는 SecurityConfig의
 * permitAll 목록에 없어 anyRequest().authenticated()에 걸려 실제 원인과 무관하게
 * 403으로 나간다(회원가입 검증 실패가 403으로 보이던 것과 같은 원인). 그래서
 * 컨트롤러에서 던질 수 있는 예외 유형을 최대한 여기서 직접 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage()));
    }

    /**
     * @Valid 검증 실패 처리.
     * 이 핸들러가 없으면 Spring이 /error로 내부 전달하는데, /error는 permitAll 목록에 없어
     * SecurityConfig의 anyRequest().authenticated()에 걸려 400이 아닌 403으로 나간다.
     * 그 탓에 "이메일 형식 오류"가 클라이언트에는 권한 없음으로 보였다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message));
    }

    /** 요청 바디를 JSON으로 읽을 수 없는 경우(깨진 인코딩, 형식 오류 등). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 형식이 올바르지 않습니다."));
    }

    /** 그 외 처리되지 않은 모든 예외 — 최소한 403으로 오해되지 않도록 500으로 명확히 응답한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("서버에 문제가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }
}
