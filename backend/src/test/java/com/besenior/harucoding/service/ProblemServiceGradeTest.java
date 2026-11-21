package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.AttemptRequest;
import com.besenior.harucoding.DTO.AttemptResultResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.Topic;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.generation.verify.CodeVerifier;
import com.besenior.harucoding.generation.verify.GradeResult;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 풀이 제출 시 숨겨진 테스트까지 포함해 채점하고, 통과 수를 응답에 담는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class ProblemServiceGradeTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private UserStreakLogRepository streakLogRepository;
    @Mock private UserCategoryStatRepository categoryStatRepository;
    @Mock private UserXpLogRepository xpLogRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private CodeVerifier codeVerifier;

    private static final List<Map<String, String>> HIDDEN =
            List.of(Map.of("input", "a = 5\nb = 7", "output", "12"));

    private AttemptRequest request(String code) {
        AttemptRequest req = new AttemptRequest();
        ReflectionTestUtils.setField(req, "answer", code);
        return req;
    }

    @Test
    void 숨겨진_테스트를_함께_넘겨_채점하고_통과_수를_응답에_담는다() {
        Problem problem = Problem.builder()
                .type(ProblemType.IMPLEMENTATION).category("Basic/Introductory").difficulty(0).language("Python")
                .title("두 수의 합").description("설명")
                .codeSkeleton("def solution(a, b):\n    {{CORE}}")
                .ioExample(Map.of("input", "a = 1\nb = 2", "output", "3"))
                .answer("return a + b").explanation("해설")
                .hiddenTests(HIDDEN)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(codeVerifier.grade(any(), eq("Python"), eq(HIDDEN)))
                .thenReturn(new GradeResult(false, 1, 2, "output_mismatch", "숨겨진 테스트 1번의 출력이 다릅니다."));
        when(topicRepository.findByName("Basic/Introductory")).thenReturn(Optional.of(Topic.builder().name("x").build()));
        when(categoryStatRepository.findByUserIdAndTopicId(eq(1L), any())).thenReturn(Optional.empty());
        when(streakLogRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        ProblemService service = new ProblemService(problemRepository, userRepository, recordRepository,
                streakLogRepository, categoryStatRepository, xpLogRepository, topicRepository,
                new ObjectMapper(), codeVerifier);
        AttemptResultResponse res = service.attempt(1L, 10L, request("return 3"));

        assertThat(res.isCorrect()).isFalse();
        assertThat(res.getTestsPassed()).isEqualTo(1);
        assertThat(res.getTestsTotal()).isEqualTo(2);
    }
}
