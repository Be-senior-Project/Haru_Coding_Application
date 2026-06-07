package com.besenior.harucoding.DTO;

import lombok.Getter;

/** 개별 문제 풀이 제출 요청. answer는 구현/디버깅=코드 문자열, 빈칸=문자열 배열. */
@Getter
public class AttemptRequest {
    private Object answer;
    private Integer timeSpentSec;
}
