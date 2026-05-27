package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.LoginRequest;
import com.besenior.harucoding.DTO.LoginResponse;
import com.besenior.harucoding.DTO.SignupRequest;
import com.besenior.harucoding.entity.RefreshToken;
import com.besenior.harucoding.repository.RefreshTokenRepository;
import com.besenior.harucoding.service.GoogleTokenVerifier.GoogleUserInfo;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.global.crypto.AesGcmEncryptor;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AesGcmEncryptor encryptor;

    @Transactional
    public LoginResponse googleLogin(String idToken) {
        GoogleUserInfo userInfo = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByGoogleId(userInfo.googleId())
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .googleId(userInfo.googleId())
                                .email(userInfo.email())
                                .emailHash(encryptor.hash(userInfo.email()))
                                .nickname(userInfo.name())
                                .profileImageUrl(userInfo.picture())
                                .build()
                ));

        return issueTokens(user);
    }

    @Transactional
    public LoginResponse signup(SignupRequest dto) {
        if (userRepository.existsByEmailHash(encryptor.hash(dto.getEmail()))) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (!dto.getPassword().equals(dto.getPasswordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = userRepository.save(
                User.builder()
                        .email(dto.getEmail())
                        .emailHash(encryptor.hash(dto.getEmail()))
                        .nickname(dto.getNickname())
                        .password(passwordEncoder.encode(dto.getPassword()))
                        .build()
        );

        return issueTokens(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest dto) {
        User user = userRepository.findByEmailHash(encryptor.hash(dto.getEmail()))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return issueTokens(user);
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        RefreshToken tokenEntity = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (tokenEntity.isExpired()) {
            throw new CustomException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private LoginResponse issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.flush();
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(user.getId())
                        .token(refreshToken)
                        .expiresAt(LocalDateTime.now().plusDays(7))
                        .build()
        );

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
