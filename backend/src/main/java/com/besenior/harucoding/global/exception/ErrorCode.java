package com.besenior.harucoding.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,      "이미 사용 중인 이메일입니다."),
    PASSWORD_MISMATCH   (HttpStatus.BAD_REQUEST,   "비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND      (HttpStatus.NOT_FOUND,     "사용자를 찾을 수 없습니다."),
    // 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하면 가입 여부가 노출되므로 하나로 묶는다.
    INVALID_CREDENTIALS (HttpStatus.UNAUTHORIZED,  "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN       (HttpStatus.UNAUTHORIZED,  "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED       (HttpStatus.UNAUTHORIZED,  "만료된 토큰입니다."),

    // 문제 세트
    PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND,   "오늘의 문제 세트가 준비되지 않았습니다."),
    ALREADY_SUBMITTED    (HttpStatus.CONFLICT,     "이미 제출한 세트입니다."),
    SET_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "오늘의 세트 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // 문제
    PROBLEM_NOT_FOUND    (HttpStatus.NOT_FOUND,    "문제를 찾을 수 없습니다."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,   "알림을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}