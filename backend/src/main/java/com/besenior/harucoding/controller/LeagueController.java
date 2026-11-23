package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.LeagueResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;
    private final JwtProvider jwtProvider;

    @GetMapping("/my")
    public ApiResponse<LeagueResponse> getMyLeague(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(leagueService.getMyLeague(userId));
    }
}
