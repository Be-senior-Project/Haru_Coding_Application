package com.besenior.harucoding.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 온보딩 답변 저장 요청. 난이도는 서버가 답변에서 계산하므로 받지 않는다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingSaveRequest {

    @NotBlank(message = "코딩 경험을 선택해주세요.")
    private String codingLevel;   // NONE / SOME / LOTS

    private boolean cotePrepared;
}
