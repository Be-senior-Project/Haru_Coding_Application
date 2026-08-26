package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.NotificationResponse;
import com.besenior.harucoding.entity.Notification;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.NotificationRepository;
import com.besenior.harucoding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 알림 목록·확인 (HOME-005). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.findAllByUserIdAndReadFalse(userId).forEach(Notification::markRead);
    }

    /** 온보딩(회원가입) 완료 시점에 환영 알림 1건을 만든다. */
    @Transactional
    public void notifyWelcome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        notificationRepository.save(Notification.builder()
                .user(user)
                .title("하루코딩에 오신 걸 환영해요!")
                .body("오늘의 문제를 풀고 첫 학습 기록을 남겨보세요.")
                .build());
    }
}
