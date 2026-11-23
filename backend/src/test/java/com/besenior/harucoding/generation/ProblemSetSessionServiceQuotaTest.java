package com.besenior.harucoding.generation;

import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.service.ProblemRecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 세트 시작 시 생성 한도·세트 크기 상한이 실제로 적용되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProblemSetSessionServiceQuotaTest {

    @Mock private ProblemRecommendationService recommendationService;
    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private GenerationPipeline generationPipeline;
    @Mock private PlatformTransactionManager txManager;

    private ProblemSetSessionService service(int dailyLimit) {
        GenerationQuota quota = new GenerationQuota(dailyLimit,
                Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        return new ProblemSetSessionService(
                recommendationService, problemRepository, userRepository, recordRepository, generationPipeline, quota,
                new TransactionTemplate(txManager));
    }

    private void noUnsolvedProblems() {
        User user = User.builder().email("t@t.com").emailHash("h").nickname("tester").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recommendationService.recommend(eq(1L), anyInt())).thenReturn(
                RecommendationResponse.builder().recommendations(List.of()).build());
    }

    @Test
    void 한도를_넘고_줄_문제도_없으면_429로_거부하고_생성하지_않는다() {
        noUnsolvedProblems();

        assertThatThrownBy(() -> service(0).startSet(1L, 4))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.GENERATION_LIMIT_EXCEEDED));
        verifyNoInteractions(generationPipeline);
    }

    @Test
    void 세트_크기는_최대_10문제로_제한된다() {
        noUnsolvedProblems();
        when(generationPipeline.generateAndSave(
                eq("Basic/Introductory"), eq(null), anyInt(), eq("Python"), eq(List.of("Implementation", "Debugging", "Fill-in-the-blank")), eq(10)))
                .thenReturn(List.of());

        service(30).startSet(1L, 1000);

        verify(generationPipeline).generateAndSave(
                eq("Basic/Introductory"), eq(null), anyInt(), eq("Python"), eq(List.of("Implementation", "Debugging", "Fill-in-the-blank")), eq(10));
    }
}
