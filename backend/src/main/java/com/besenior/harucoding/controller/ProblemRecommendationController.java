package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.ProblemEmbeddingService;
import com.besenior.harucoding.service.ProblemRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class ProblemRecommendationController {

    private final ProblemRecommendationService recommendationService;
    private final ProblemEmbeddingService embeddingService;
    private final JwtProvider jwtProvider;

    /** 로그인 유저의 역량 기반 추천 (기본 5문제). */
    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "5") int limit) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(recommendationService.recommend(userId, limit));
    }

    /** [관리/초기화] 임베딩이 없는 문제를 일괄 생성. 생성 건수 반환. */
    @PostMapping("/embeddings/backfill")
    public ApiResponse<Integer> backfillEmbeddings() {
        return ApiResponse.success("임베딩 생성 완료", embeddingService.backfillMissing());
    }
}
