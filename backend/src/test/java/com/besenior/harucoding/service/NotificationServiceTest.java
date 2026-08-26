package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.NotificationResponse;
import com.besenior.harucoding.entity.Notification;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.NotificationRepository;
import com.besenior.harucoding.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** HOME-005: 알림 목록·확인 로직. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository);
    }

    @Test
    void 목록은_최신순_레포지토리_결과를_그대로_응답으로_변환한다() {
        Notification n = Notification.builder().user(new User()).title("제목").body("본문").build();
        when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n));

        List<NotificationResponse> result = service.getNotifications(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("제목");
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void 단건_확인_처리시_읽음_상태가_바뀐다() {
        Notification n = Notification.builder().user(new User()).title("제목").body("본문").build();
        when(notificationRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(n));

        service.markRead(1L, 5L);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void 본인_소유가_아닌_알림을_확인하면_예외가_발생한다() {
        when(notificationRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(1L, 5L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void 전체_확인_처리는_안읽은_알림만_읽음으로_바꾼다() {
        Notification unread = Notification.builder().user(new User()).title("A").body("a").build();
        when(notificationRepository.findAllByUserIdAndReadFalse(1L)).thenReturn(List.of(unread));

        service.markAllRead(1L);

        assertThat(unread.isRead()).isTrue();
    }

    @Test
    void 온보딩_완료시_환영_알림이_저장된다() {
        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.notifyWelcome(1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getTitle()).isNotBlank();
    }
}
