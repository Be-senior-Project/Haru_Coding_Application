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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 풀이 제출 시 채점 방식(실행 채점 / 빈칸 문자열 비교)과 통과 수 응답을 검증한다. */
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
    @Mock private LeagueService leagueService;

    private static final List<Map<String, String>> HIDDEN =
            List.of(Map.of("input", "a = 5\nb = 7", "output", "12"));

    private static final String BLANK_SKELETON =
            "def solution(n, arr):\n    cnt = 0\n    for x in arr:\n        if {{BLANK_1}}:\n            {{BLANK_2}}\n    return cnt";

    private AttemptRequest request(Object answer) {
        AttemptRequest req = new AttemptRequest();
        ReflectionTestUtils.setField(req, "answer", answer);
        return req;
    }

    private ProblemService service() {
        return new ProblemService(problemRepository, userRepository, recordRepository,
                streakLogRepository, categoryStatRepository, xpLogRepository, topicRepository,
                new ObjectMapper(), codeVerifier, leagueService);
    }

    /** attempt()가 채점 뒤 기록·통계를 남길 때 필요한 공통 응답. */
    private void stubAttempt(Problem problem) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(problemRepository.findById(10L)).thenReturn(Optional.of(problem));
        when(topicRepository.findByName("Basic/Introductory")).thenReturn(Optional.of(Topic.builder().name("x").build()));
        when(categoryStatRepository.findByUserIdAndTopicId(eq(1L), any())).thenReturn(Optional.empty());
        when(streakLogRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());
    }

    private Problem implementationProblem() {
        return Problem.builder()
                .type(ProblemType.IMPLEMENTATION).category("Basic/Introductory").difficulty(0).language("Python")
                .title("두 수의 합").description("설명")
                .codeSkeleton("def solution(a, b):\n    {{CORE}}")
                .ioExample(Map.of("input", "a = 1\nb = 2", "output", "3"))
                .answer("return a + b").explanation("해설")
                .hiddenTests(HIDDEN)
                .build();
    }

    private Problem blankProblem(List<Map<String, String>> hiddenTests) {
        return Problem.builder()
                .type(ProblemType.FILL_IN_THE_BLANK).category("Basic/Introductory").difficulty(0).language("Python")
                .title("숫자 개수 세기").description("설명")
                .codeSkeleton(BLANK_SKELETON)
                .ioExample(Map.of("input", "n = 3\narr = [3, 1, 3, 2, 3]", "output", "3"))
                .answer(List.of("x == n", "cnt += 1")).explanation("해설")
                .hiddenTests(hiddenTests)
                .build();
    }

    @Test
    void 숨겨진_테스트를_함께_넘겨_채점하고_통과_수를_응답에_담는다() {
        stubAttempt(implementationProblem());
        when(codeVerifier.grade(any(), eq("Python"), eq(HIDDEN)))
                .thenReturn(new GradeResult(false, 1, 2, "output_mismatch", "숨겨진 테스트 1번의 출력이 다릅니다."));

        AttemptResultResponse res = service().attempt(1L, 10L, request("return 3"));

        assertThat(res.isCorrect()).isFalse();
        assertThat(res.getTestsPassed()).isEqualTo(1);
        assertThat(res.getTestsTotal()).isEqualTo(2);
    }

    @Test
    void 구현_답안은_들여쓰기를_붙이지_않고_그대로_채점기에_넘긴다() {
        // 들여쓰기는 CodeComposer가 한 번만 붙인다. 여기서도 붙이면 두 번 들여써져 문법 오류가 난다.
        stubAttempt(implementationProblem());
        ArgumentCaptor<JsonNode> node = ArgumentCaptor.forClass(JsonNode.class);
        when(codeVerifier.grade(node.capture(), eq("Python"), eq(HIDDEN)))
                .thenReturn(new GradeResult(false, 0, 2, "output_mismatch", "예시 출력이 다릅니다."));

        service().attempt(1L, 10L, request("x = a + b\nreturn x"));

        assertThat(node.getValue().get("Answer").asText()).isEqualTo("x = a + b\nreturn x");
    }

    @Test
    void 숨겨진_테스트가_있는_빈칸_문제는_실행_채점해_다른_표현도_정답_처리한다() {
        List<Map<String, String>> hidden = List.of(Map.of("input", "n = 1\narr = [1, 1]", "output", "2"));
        stubAttempt(blankProblem(hidden));
        when(codeVerifier.grade(any(), eq("Python"), eq(hidden))).thenReturn(new GradeResult(true, 2, 2, null, null));

        AttemptResultResponse res = service().attempt(1L, 10L, request(List.of("n == x", "cnt += 1")));

        assertThat(res.isCorrect()).isTrue();
        assertThat(res.getTestsTotal()).isEqualTo(2);
        verify(codeVerifier).grade(any(), eq("Python"), eq(hidden));
    }

    @Test
    void 숨겨진_테스트가_없는_빈칸_문제는_빈칸별_문자열_비교로_채점한다() {
        stubAttempt(blankProblem(null));

        AttemptResultResponse spaced = service().attempt(1L, 10L, request(List.of("x==n", "cnt+=1")));
        AttemptResultResponse other = service().attempt(1L, 10L, request(List.of("n == x", "cnt += 1")));

        assertThat(spaced.isCorrect()).isTrue();   // 공백 차이는 무시
        assertThat(other.isCorrect()).isFalse();   // 예시 1개로는 다른 표현의 정오를 가릴 수 없어 문자열 기준
        verifyNoInteractions(codeVerifier);
    }
}
