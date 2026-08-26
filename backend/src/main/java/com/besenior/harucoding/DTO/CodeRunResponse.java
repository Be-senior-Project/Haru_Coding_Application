package com.besenior.harucoding.DTO;

import com.besenior.harucoding.generation.verify.VerifyResult;
import lombok.Builder;
import lombok.Getter;

/** "실행" 버튼(SOLVE-007) 결과. 채점(attempt)과 달리 이력에 남기지 않는다. */
@Getter
@Builder
public class CodeRunResponse {
    private boolean ok;              // 예시 입력 기준 정답과 일치했는지
    private String actualOutput;     // 실제 stdout (컴파일 실패 등 실행 자체가 안 됐으면 null)
    private String reason;           // compile_error | runtime_error | timeout | output_mismatch | internal_error
    private String detail;

    public static CodeRunResponse from(VerifyResult r) {
        return CodeRunResponse.builder()
                .ok(r.ok())
                .actualOutput(r.actualOutput())
                .reason(r.reason())
                .detail(r.detail())
                .build();
    }
}
