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

    /**
     * 기존 유저 개인화 추천
     * 메인 화면 진입 시 호출 — 풀이 이력 + 카테고리 성취도 기반
     */
    @PostMapping("/personalized")
    public ResponseEntity<RecommendationFilterDto> personalized(
            @RequestBody UserProfileDto profile) {
        return ResponseEntity.ok(recommendationService.recommendPersonalized(profile));
    }
}
