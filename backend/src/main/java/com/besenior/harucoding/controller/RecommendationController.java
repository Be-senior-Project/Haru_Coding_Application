package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.RecommendationFilterDto;
import com.besenior.harucoding.DTO.UserProfileDto;
import com.besenior.harucoding.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 신규 유저 온보딩 추천
     * 가입 완료 시 호출 — coding_level, cote_prepared 기반
     */
    @PostMapping("/onboarding")
    public ResponseEntity<RecommendationFilterDto> onboarding(
            @RequestBody UserProfileDto profile) {
        return ResponseEntity.ok(recommendationService.recommendOnboarding(profile));
    }
}
