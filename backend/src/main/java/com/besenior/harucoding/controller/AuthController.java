package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.GoogleLoginRequest;
import com.besenior.harucoding.DTO.LoginRequest;
import com.besenior.harucoding.DTO.LoginResponse;
import com.besenior.harucoding.DTO.SignupRequest;
import com.besenior.harucoding.service.AuthService;
import com.besenior.harucoding.global.jwt.JwtProvider;
import com.besenior.harucoding.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @PostMapping("/google")
    public ApiResponse<LoginResponse> googleLogin(@RequestBody @Valid GoogleLoginRequest request) {
        LoginResponse response = authService.googleLogin(request.getIdToken());
        return ApiResponse.success(response);
    }

    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signup(@RequestBody @Valid SignupRequest dto) {
        return ApiResponse.success(authService.signup(dto));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest dto) {
        return ApiResponse.success(authService.login(dto));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestHeader("Authorization") String token) {
        String refreshToken = token.replace("Bearer ", "");
        return ApiResponse.success(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String token) {
        String accessToken = token.replace("Bearer ", "");
        Long userId = jwtProvider.getUserId(accessToken);
        authService.logout(userId);
        return ApiResponse.success("로그아웃 성공", null);
    }
}
