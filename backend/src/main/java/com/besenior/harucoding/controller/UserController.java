package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.UpdateProfileRequest;
import com.besenior.harucoding.DTO.UserProfileResponse;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @RequestHeader("Authorization") String token) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(userService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody UpdateProfileRequest request) {
        Long userId = jwtProvider.getUserId(token.replace("Bearer ", ""));
        return ApiResponse.success(userService.updateProfile(userId, request));
    }
}