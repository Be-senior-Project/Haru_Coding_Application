package com.besenior.harucoding.service;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.VerifyResult;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SOLVE-007: "실행" 버튼이 채점(attempt)과 달리 이력 저장 없이 CodeVerifier 결과만 그대로 돌려주는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProblemServiceRunTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private UserStreakLogRepository streakLogRepository;
    @Mock private UserCategoryStatRepository categoryStatRepository;
    @Mock private UserXpLogRepository xpLogRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private CodeVerifier codeVerifier;

    private ProblemService service() {
        return new ProblemService(problemRepository, userRepository, recordRepository, streakLogRepository,
                categoryStatRepository, xpLogRepository, topicRepository, new ObjectMapper(), codeVerifier);
    }

    private Problem runnableProblem() {
        return Problem.builder()
                .type(ProblemType.IMPLEMENTATION)
                .category("Basic/Introductory")
                .difficulty(0)
                .language("Python")
                .title("두 수의 합")
                .description("설명")
                .codeSkeleton("def solution(a, b):\n    {{CORE}}")
                .ioExample(Map.of("input", "a = 1\nb = 2", "output", "3"))
                .answer("return a + b")
                .explanation("해설")
                .build();
    }

    @Test
    void 실행_결과는_채점_이력을_저장하지_않고_VerifyResult를_그대로_반환한다() {
        Problem problem = runnableProblem();
        when(problemRepository.findById(1L)).thenReturn(Optional.of(problem));
        VerifyResult expected = VerifyResult.pass("3");
        when(codeVerifier.verify(any(), eq("Python"))).thenReturn(expected);

        VerifyResult result = service().run(1L, "return a + b");

        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(recordRepository, streakLogRepository, categoryStatRepository, xpLogRepository);
    }

    @Test
    void 실행에_필요한_스켈레톤_시그니처_IO가_없으면_검증기를_호출하지_않고_internal_error를_반환한다() {
        Problem problem = Problem.builder()
                .type(ProblemType.IMPLEMENTATION)
                .category("Basic/Introductory")
                .difficulty(0)
                .language("Python")
                .title("설명 없는 문제")
                .description("설명")
                .answer("pass")
                .explanation("해설")
                .build(); // codeSkeleton/ioExample 없음
        when(problemRepository.findById(2L)).thenReturn(Optional.of(problem));

        VerifyResult result = service().run(2L, "아무 코드");

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("internal_error");
        verifyNoInteractions(codeVerifier);
    }
}
