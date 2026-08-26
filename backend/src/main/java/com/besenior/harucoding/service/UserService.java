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
    private final NotificationService notificationService;

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

        user.updateProfile(request.getNickname(), request.getPreferredLanguage(), request.getFcmToken(),
                request.getDailyGoalCount(), request.getDifficultyLevel());
        userRepository.save(user);

        long totalSolved  = recordRepository.countTotalByUserId(userId);
        long correctCount = recordRepository.countCorrectByUserId(userId);

        return UserProfileResponse.from(user, totalSolved, correctCount);
    }

    /**
     * 온보딩 답변을 저장한다. 가입 직후 "가입하기"를 누른 시점에만 호출된다.
     * 계산(난이도·문구)은 RecommendationService가 이미 끝냈고, 여기서는 원본 답변만 기록한다.
     */
    @Transactional
    public void saveOnboarding(Long userId, String codingLevel, boolean cotePrepared) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.updateOnboarding(codingLevel, cotePrepared);
        userRepository.save(user);
        notificationService.notifyWelcome(userId);
    }
}