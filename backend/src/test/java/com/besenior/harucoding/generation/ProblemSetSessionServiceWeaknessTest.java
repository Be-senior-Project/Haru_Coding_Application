package com.besenior.harucoding.generation;

import com.besenior.harucoding.DTO.ProblemResponse;
import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.DTO.RecommendationResponse.CompetencySummary;
import com.besenior.harucoding.DTO.RecommendationResponse.RecommendedProblem;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.service.ProblemRecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 오늘의 세트: 약점 영역이 있으면 절반을 약점 영역 문제로 채우고, 모자라면 약점 영역으로 생성한다. */
@ExtendWith(MockitoExtension.class)
class ProblemSetSessionServiceWeaknessTest {

    @Mock private ProblemRecommendationService recommendationService;
    @Mock private ProblemRepository problemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private GenerationPipeline generationPipeline;

    private static final String ALGO = "Algorithm/Data Structure";
    private static final List<String> ALL_TYPES = List.of("Implementation", "Debugging", "Fill-in-the-blank");

    private ProblemSetSessionService service() {
        GenerationQuota quota = new GenerationQuota(30,
                Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        return new ProblemSetSessionService(
                recommendationService, problemRepository, userRepository, recordRepository, generationPipeline, quota);
    }

    private static Problem problem(long id, String category, String subcategory, String language) {
        Problem p = Problem.builder()
                .type(ProblemType.IMPLEMENTATION).category(category).subcategory(subcategory)
                .difficulty(0).language(language).title("문제 " + id).description("설명")
                .answer("pass").explanation("해설").build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static UserProblemRecord wrong(Problem p, int minute) {
        UserProblemRecord r = UserProblemRecord.builder().problem(p).isCorrect(false).timeSpentSec(10).build();
        ReflectionTestUtils.setField(r, "solvedAt", LocalDateTime.of(2026, 11, 21, 12, minute));
        return r;
    }

    /** DFS/BFS 세 문제를 처음에 모두 틀린 사용자 → 약점: 알고리즘/자료구조 > DFS/BFS. 추천 엔진은 기초 문제 2개를 준다. */
    private void givenWeakInDfsBfs(Problem a1, Problem a2, Problem a3) {
        User user = User.builder().email("t@t.com").emailHash("h").nickname("tester").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L))
                .thenReturn(List.of(wrong(a3, 3), wrong(a2, 2), wrong(a1, 1)));
        when(recommendationService.recommend(eq(1L), anyInt())).thenReturn(RecommendationResponse.builder()
                .summary(CompetencySummary.builder().estimatedLevel(0).build())
                .recommendations(List.of(
                        RecommendedProblem.builder().problemId(20L).build(),
                        RecommendedProblem.builder().problemId(21L).build()))
                .build());
    }

    @Test
    void 약점_영역이_있으면_세트의_절반을_약점_영역의_안_푼_문제로_채운다() {
        Problem a1 = problem(1, ALGO, "DFS/BFS", "Python");
        Problem a2 = problem(2, ALGO, "DFS/BFS", "Python");
        Problem a3 = problem(3, ALGO, "DFS/BFS", "Python");
        givenWeakInDfsBfs(a1, a2, a3);
        Problem greedy = problem(11, ALGO, "Greedy", "Python");
        Problem dfs = problem(10, ALGO, "DFS/BFS", "Python");
        Problem javaDfs = problem(12, ALGO, "DFS/BFS", "Java");         // 언어가 달라 제외
        when(problemRepository.findByCategory(ALGO)).thenReturn(List.of(a1, a2, a3, greedy, dfs, javaDfs));
        when(problemRepository.findAllById(List.of(20L, 21L))).thenReturn(List.of(
                problem(20, "Basic/Introductory", null, "Python"),
                problem(21, "Basic/Introductory", null, "Python")));

        List<ProblemResponse> set = service().startSet(1L, 4);

        // 약점 몫 2문제(같은 하위 분류 우선) + 추천 2문제, 생성 없음
        assertThat(set).extracting(ProblemResponse::getId).containsExactly(10L, 11L, 20L, 21L);
        assertThat(set).extracting(ProblemResponse::getWeakArea).containsExactly("DFS/BFS", "Greedy", null, null);
        verifyNoInteractions(generationPipeline);
    }

    @Test
    void 약점_영역에_안_푼_문제가_모자라면_부족분을_약점_영역으로_생성한다() {
        Problem a1 = problem(1, ALGO, "DFS/BFS", "Python");
        Problem a2 = problem(2, ALGO, "DFS/BFS", "Python");
        Problem a3 = problem(3, ALGO, "DFS/BFS", "Python");
        givenWeakInDfsBfs(a1, a2, a3);
        when(problemRepository.findByCategory(ALGO)).thenReturn(List.of(a1, a2, a3));   // 모두 이미 푼 문제
        when(problemRepository.findAllById(List.of(20L, 21L))).thenReturn(List.of(
                problem(20, "Basic/Introductory", null, "Python"),
                problem(21, "Basic/Introductory", null, "Python")));
        when(generationPipeline.generateAndSave(ALGO, "DFS/BFS", 0, "Python", ALL_TYPES, 2))
                .thenReturn(List.of(problem(30, ALGO, "DFS/BFS", "Python"), problem(31, ALGO, "DFS/BFS", "Python")));

        List<ProblemResponse> set = service().startSet(1L, 4);

        verify(generationPipeline).generateAndSave(ALGO, "DFS/BFS", 0, "Python", ALL_TYPES, 2);
        assertThat(set).extracting(ProblemResponse::getId).containsExactly(20L, 21L, 30L, 31L);
        assertThat(set).extracting(ProblemResponse::getWeakArea).containsExactly(null, null, "DFS/BFS", "DFS/BFS");
    }
}
