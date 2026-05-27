package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.UpdateProfileRequest;
import com.besenior.harucoding.DTO.UserProfileResponse;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProblemRecordRepository recordRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        long totalSolved  = recordRepository.countTotalByUserId(userId);
        long correctCount = recordRepository.countCorrectByUserId(userId);

        return UserProfileResponse.from(user, totalSolved, correctCount);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.updateProfile(request.getNickname(), request.getPreferredLanguage(), request.getFcmToken(), null);
        userRepository.save(user);

        long totalSolved  = recordRepository.countTotalByUserId(userId);
        long correctCount = recordRepository.countCorrectByUserId(userId);

        return UserProfileResponse.from(user, totalSolved, correctCount);
    }
}