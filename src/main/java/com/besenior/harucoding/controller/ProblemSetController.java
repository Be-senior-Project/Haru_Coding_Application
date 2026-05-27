package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.ProblemSetResponse;
import com.besenior.harucoding.DTO.SubmitAnswerRequest;
import com.besenior.harucoding.DTO.SubmitResultResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.ProblemSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem-sets")
public class ProblemSetController {

    private final ProblemSetService problemSetService;
    private final JwtProvider jwtProvider;

    @GetMapping("/today")
    public ApiResponse<ProblemSetResponse> getTodaySet() {
        return ApiResponse.success(problemSetService.getTodaySet());
    }

    @PostMapping("/{setId}/submit")
    public ApiResponse<SubmitResultResponse> submit(
            @RequestHeader("Authorization") String token,
            @PathVariable Long setId,
            @RequestBody SubmitAnswerRequest request) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(problemSetService.submit(userId, setId, request));
    }
}