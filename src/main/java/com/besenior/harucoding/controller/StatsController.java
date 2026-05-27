package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.StatsResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;
    private final JwtProvider jwtProvider;

    @GetMapping("/me")
    public ApiResponse<StatsResponse> getMyStats(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(statsService.getStats(userId));
    }
}