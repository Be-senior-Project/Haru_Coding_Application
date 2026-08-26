package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.NotificationResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 알림 목록·확인 (HOME-005). 인증 필요. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtProvider jwtProvider;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(notificationService.getNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        notificationService.markRead(userId, id);
        return ApiResponse.success("확인 처리 완료", null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        notificationService.markAllRead(userId);
        return ApiResponse.success("전체 확인 처리 완료", null);
    }
}
