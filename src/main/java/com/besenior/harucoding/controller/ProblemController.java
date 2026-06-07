package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.AttemptRequest;
import com.besenior.harucoding.DTO.AttemptResultResponse;
import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;
    private final JwtProvider jwtProvider;

    /** 문제 은행 목록 (선택적 필터). 정답 미포함. 공개. */
    @GetMapping
    public ApiResponse<List<ProblemResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) ProblemType type,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(problemService.search(category, difficulty, type, language, limit));
    }

    /** 개별 문제 조회 (풀이용, 정답 미포함). 공개. */
    @GetMapping("/{id}")
    public ApiResponse<ProblemResponse> get(@PathVariable Long id) {
        return ApiResponse.success(problemService.getProblem(id));
    }

    /** 풀이 제출 → 채점 + 기록. 인증 필요. */
    @PostMapping("/{id}/attempt")
    public ApiResponse<AttemptResultResponse> attempt(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody AttemptRequest request) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(problemService.attempt(userId, id, request));
    }
}
