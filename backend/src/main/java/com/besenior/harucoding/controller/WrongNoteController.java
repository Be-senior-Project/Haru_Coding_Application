package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.WrongNoteResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.WrongNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 오답노트 (문제은행 탭 > 오답노트). 인증 필요. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wrong-notes")
public class WrongNoteController {

    private final WrongNoteService wrongNoteService;
    private final JwtProvider jwtProvider;

    @GetMapping
    public ApiResponse<List<WrongNoteResponse>> getWrongNotes(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(wrongNoteService.getWrongNotes(userId));
    }
}
