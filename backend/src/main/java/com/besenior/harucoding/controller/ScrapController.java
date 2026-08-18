package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.ScrapResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 문제 스크랩 (문제은행 탭 > 스크랩). 인증 필요. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scraps")
public class ScrapController {

    private final ScrapService scrapService;
    private final JwtProvider jwtProvider;

    /** 스크랩 목록 (최신순). */
    @GetMapping
    public ApiResponse<List<ScrapResponse>> getScraps(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(scrapService.getScraps(userId));
    }

    /** 스크랩 토글. 응답의 scrapped가 토글 후 상태. */
    @PostMapping("/{problemId}")
    public ApiResponse<Map<String, Boolean>> toggleScrap(
            @RequestHeader("Authorization") String token,
            @PathVariable Long problemId) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        boolean scrapped = scrapService.toggleScrap(userId, problemId);
        return ApiResponse.success(Map.of("scrapped", scrapped));
    }

    /** 특정 문제의 스크랩 여부 (문제 풀이 화면 북마크 아이콘 초기 상태용). */
    @GetMapping("/{problemId}")
    public ApiResponse<Map<String, Boolean>> isScrapped(
            @RequestHeader("Authorization") String token,
            @PathVariable Long problemId) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(Map.of("scrapped", scrapService.isScrapped(userId, problemId)));
    }
}
