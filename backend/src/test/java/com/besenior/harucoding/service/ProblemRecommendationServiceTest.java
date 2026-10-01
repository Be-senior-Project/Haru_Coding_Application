package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.RecommendationResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.ProblemEmbeddingRepository;
import com.besenior.harucoding.repository.ProblemRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * MY-016(선호 언어 필터 대소문자 불일치)과 MY-019(유저 지정 난이도)가
 * 구조적 폴백 추천(structuredRecommend) 경로에 실제로 반영되는지 검증한다.
 * 이력이 없는 유저(cold start)라 similarityRecommend는 항상 스킵되고 구조적 폴백만 탄다.
 */
@ExtendWith(MockitoExtension.class)
class ProblemRecommendationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private ProblemEmbeddingRepository embeddingRepository;

    private ProblemRecommendationService service;

    private Problem problem(long id, String language, int difficulty) {
        Problem p = Problem.builder()
                .type(ProblemType.IMPLEMENTATION)
                .category("Basic/Introductory")
                .difficulty(difficulty)
                .language(language)
                .title("문제 " + id)
                .description("설명")
                .codeSkeleton("def solution(): pass")
                .answer("pass")
                .explanation("해설")
                .build();
        // @Id는 IDENTITY 전략이라 빌더에 없다 — 테스트에서 결과 매칭용으로만 리플렉션으로 채운다.
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private User userWith(String preferredLanguage, String difficultyLevel) {
        User u = User.builder()
                .email("t@t.com").emailHash("h").nickname("tester")
                .build();
        u.updateProfile(null, preferredLanguage, null, null, difficultyLevel);
        return u;
    }

    @Test
    void 선호_언어가_대문자여도_해당_언어_문제만_추천된다() {
        // MY-016: 이전에는 "PYTHON" == "Python" 비교가 항상 false라 결과가 0건이었다.
        service = new ProblemRecommendationService(userRepository, recordRepository, problemRepository, embeddingRepository);
        User user = userWith("PYTHON", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(problemRepository.findAll()).thenReturn(List.of(
                problem(10L, "Python", 0),
                problem(20L, "Java", 0)
        ));

        RecommendationResponse res = service.recommend(1L, 5);

        assertThat(res.getRecommendations()).hasSize(1);
        assertThat(res.getRecommendations().get(0).getLanguage()).isEqualTo("Python");
    }

    @Test
    void 선호_언어가_없으면_모든_언어의_문제가_후보에_포함된다() {
        service = new ProblemRecommendationService(userRepository, recordRepository, problemRepository, embeddingRepository);
        User user = userWith(null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(problemRepository.findAll()).thenReturn(List.of(
                problem(10L, "Python", 0),
                problem(20L, "Java", 0)
        ));

        RecommendationResponse res = service.recommend(1L, 5);

        assertThat(res.getRecommendations()).hasSize(2);
    }

    @Test
    void 유저가_난이도를_L5로_지정하면_자동_추정_대신_레벨2가_사용된다() {
        // MY-019: codingLevel=NONE만 있으면 자동 추정 레벨은 0이지만, difficultyLevel="L5"를 지정하면 2여야 한다.
        service = new ProblemRecommendationService(userRepository, recordRepository, problemRepository, embeddingRepository);
        User user = userWith(null, "L5");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(problemRepository.findAll()).thenReturn(List.of(problem(10L, "Python", 2)));

        RecommendationResponse res = service.recommend(1L, 5);

        assertThat(res.getSummary().getEstimatedLevel()).isEqualTo(2);
    }

    @Test
    void 난이도_미지정시_자동_추정값이_사용된다() {
        service = new ProblemRecommendationService(userRepository, recordRepository, problemRepository, embeddingRepository);
        User user = userWith(null, null); // codingLevel 기본값 NONE, cotePrepared 기본값 false → 자동 추정 레벨 0

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(problemRepository.findAll()).thenReturn(List.of(problem(10L, "Python", 0)));

        RecommendationResponse res = service.recommend(1L, 5);

        assertThat(res.getSummary().getEstimatedLevel()).isEqualTo(0);
    }
}
