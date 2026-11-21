package com.besenior.harucoding.generation.verify;

import java.util.List;

/** 언어별 코드 실행기. 여러 테스트 입력을 한 번의 컴파일·실행으로 처리한다(컴파일 비용을 테스트 수만큼 반복하지 않기 위해). */
public interface Runner {

    /**
     * 입력마다 solution을 호출하고, 반환값을 JSON 한 줄씩 출력한 결과를 돌려준다.
     *
     * @param answerCode 실행할 완성 코드 (solution 함수 포함)
     * @param ioInputs   입력별 변수 선언문(해당 언어 문법). 각 입력은 독립된 블록에서 실행된다
     * @param signature  solution 시그니처 (호출 인자 추출용)
     */
    ExecutionResult execute(String answerCode, List<String> ioInputs, String signature);
}
