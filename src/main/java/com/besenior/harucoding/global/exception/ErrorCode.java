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
    INVALID_PASSWORD    (HttpStatus.UNAUTHORIZED,  "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN       (HttpStatus.UNAUTHORIZED,  "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED       (HttpStatus.UNAUTHORIZED,  "만료된 토큰입니다."),

    // 문제 세트
    PROBLEM_SET_NOT_FOUND(HttpStatus.NOT_FOUND,   "오늘의 문제 세트가 준비되지 않았습니다."),
    ALREADY_SUBMITTED    (HttpStatus.CONFLICT,     "이미 제출한 세트입니다."),

    // 문제
    PROBLEM_NOT_FOUND    (HttpStatus.NOT_FOUND,    "문제를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}