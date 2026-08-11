package com.besenior.harucoding.generation.verify;

/** 언어별 코드 실행 검증기. */
public interface Runner {

    /**
     * @param answerCode     실행할 완성 코드 (solution 함수 포함)
     * @param ioInput        해당 언어 문법의 변수 선언문
     * @param expectedOutput 기대 stdout (JSON 직렬화 형태)
     * @param signature      solution 시그니처 (호출 인자 추출용)
     */
    VerifyResult run(String answerCode, String ioInput, String expectedOutput, String signature);
}
