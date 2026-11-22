package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.StatsResponse;
import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.repository.TopicRepository;
import com.besenior.harucoding.repository.UserProblemRecordRepository;
import com.besenior.harucoding.repository.UserRepository;
import com.besenior.harucoding.repository.UserStreakLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** STAT-002: 기간 탭별 집계, 문제별 첫 제출 기준 집계, 약점 영역 기준을 검증한다. */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private UserStreakLogRepository streakLogRepository;
    @Mock private TopicRepository topicRepository;

    private StatsService service;

    private static final LocalDateTime NOW = LocalDateTime.of(LocalDate.now(), java.time.LocalTime.NOON);

    private UserProblemRecord recordAt(long problemId, LocalDateTime solvedAt, boolean correct, String category) {
        Problem problem = Problem.builder()
                .type(ProblemType.IMPLEMENTATION)
                .category(category)
                .difficulty(0)
                .language("Python")
                .title("문제")
                .description("설명")
                .answer("pass")
                .explanation("해설")
                .build();
        ReflectionTestUtils.setField(problem, "id", problemId);
        UserProblemRecord record = UserProblemRecord.builder()
                .user(null)
                .problem(problem)
                .isCorrect(correct)
                .timeSpentSec(30)
                .xpEarned(correct ? 10 : 0)
                .build();
        ReflectionTestUtils.setField(record, "solvedAt", solvedAt);
        return record;
    }

    /** 저장소는 최신순으로 돌려준다. */
    private void givenRecords(List<UserProblemRecord> records) {
        List<UserProblemRecord> newestFirst = new ArrayList<>(records);
        newestFirst.sort(Comparator.comparing(UserProblemRecord::getSolvedAt).reversed());
        service = new StatsService(userRepository, recordRepository, streakLogRepository, topicRepository);
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(newestFirst);
        when(streakLogRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());
    }

    @Test
    void 기간별로_풀이_수_집계가_달라진다() {
        givenRecords(List.of(
                recordAt(1, NOW, true, "알고리즘"),                       // 오늘 → 주간/월간/연간/전체 모두 포함
                recordAt(2, NOW.minusMonths(2), true, "알고리즘"),         // 이번 달 밖, 올해 안 → 연간/전체만
                recordAt(3, NOW.minusYears(2), false, "알고리즘")          // 작년 → 전체만
        ));
        when(topicRepository.findByName("알고리즘")).thenReturn(Optional.empty());

        StatsResponse all = service.getStats(1L, "ALL");
        StatsResponse year = service.getStats(1L, "YEAR");
        StatsResponse month = service.getStats(1L, "MONTH");
        StatsResponse week = service.getStats(1L, "WEEK");

        assertThat(all.getTotalSolved()).isEqualTo(3);
        assertThat(year.getTotalSolved()).isEqualTo(2);
        assertThat(month.getTotalSolved()).isEqualTo(1);
        assertThat(week.getTotalSolved()).isEqualTo(1);
    }

    @Test
    void 풀이_기록이_없으면_모든_기간에서_0으로_집계된다() {
        givenRecords(List.of());

        StatsResponse res = service.getStats(1L, "WEEK");

        assertThat(res.getTotalSolved()).isZero();
        assertThat(res.getAccuracyRate()).isZero();
        assertThat(res.getCategoryStats()).isEmpty();
        assertThat(res.getWeakCategory()).isNull();
    }

    @Test
    void 같은_문제를_다시_제출해도_푼_문제_수와_정답률은_첫_제출로_센다() {
        givenRecords(List.of(
                recordAt(1, NOW.minusHours(3), false, "A"),   // 1번: 처음엔 틀림
                recordAt(1, NOW.minusHours(2), true, "A"),    // 1번: 다시 풀어 맞힘 (세지 않음)
                recordAt(1, NOW.minusHours(1), true, "A"),    // 1번: 또 제출 (세지 않음)
                recordAt(2, NOW, true, "A")));                // 2번: 처음에 맞힘
        when(topicRepository.findByName(anyString())).thenReturn(Optional.empty());

        StatsResponse res = service.getStats(1L, "ALL");

        assertThat(res.getTotalSolved()).isEqualTo(2);
        assertThat(res.getCorrectCount()).isEqualTo(1);
        assertThat(res.getAccuracyRate()).isEqualTo(50.0);
        assertThat(res.getCategoryStats().get(0).getTotalSolved()).isEqualTo(2);
        // 최근 풀이 목록은 실제 제출 기록이라 다시 푼 것도 보인다
        assertThat(res.getRecentRecords()).hasSize(4);
    }

    @Test
    void 정답률이_높으면_영역이_하나뿐이어도_약점으로_보지_않는다() {
        givenRecords(List.of(
                recordAt(1, NOW, true, "Basic/Introductory"),
                recordAt(2, NOW, true, "Basic/Introductory"),
                recordAt(3, NOW, true, "Basic/Introductory"),
                recordAt(4, NOW, false, "Basic/Introductory")));   // 75%
        when(topicRepository.findByName(anyString())).thenReturn(Optional.empty());

        assertThat(service.getStats(1L, "ALL").getWeakCategory()).isNull();
    }

    @Test
    void 유형별_난이도별_정답률과_날짜별_풀이_수를_돌려준다() {
        UserProblemRecord debugging = recordAt(2, NOW, false, "A");
        ReflectionTestUtils.setField(debugging.getProblem(), "type", ProblemType.DEBUGGING);
        ReflectionTestUtils.setField(debugging.getProblem(), "difficulty", 2);
        givenRecords(List.of(
                recordAt(1, NOW, true, "A"),                  // 구현·난이도 0·정답
                debugging,                                    // 디버깅·난이도 2·오답
                recordAt(3, NOW.minusYears(2), true, "A")));  // 작년 → 이번 달 집계에서 빠짐
        when(topicRepository.findByName(anyString())).thenReturn(Optional.empty());

        StatsResponse all = service.getStats(1L, "ALL");
        StatsResponse month = service.getStats(1L, "MONTH");

        // 키 순서가 고정이고, 기록이 없는 유형·난이도도 0으로 들어간다
        assertThat(all.getTypeStats()).extracting(StatsResponse.BreakdownStat::getKey)
                .containsExactly("IMPLEMENTATION", "DEBUGGING", "FILL_IN_THE_BLANK");
        assertThat(all.getTypeStats()).extracting(StatsResponse.BreakdownStat::getTotalSolved).containsExactly(2, 1, 0);
        assertThat(all.getDifficultyStats()).extracting(StatsResponse.BreakdownStat::getKey).containsExactly("0", "1", "2");
        assertThat(all.getDifficultyStats()).extracting(StatsResponse.BreakdownStat::getCorrectCount).containsExactly(2, 0, 0);
        assertThat(all.getDailyActivity()).isEmpty();                       // 전체 기간은 날짜별 집계를 보내지 않음
        assertThat(month.getDailyActivity()).containsEntry(LocalDate.now().toString(), 2);
        assertThat(month.getDailyActivity()).hasSize(1);
    }

    @Test
    void 기준_미만_영역_중_정답률이_가장_낮은_영역을_약점으로_돌려준다() {
        givenRecords(List.of(
                // A: 3문제 중 1개 정답(33%) → 약점 후보
                recordAt(1, NOW, true, "A"), recordAt(2, NOW, false, "A"), recordAt(3, NOW, false, "A"),
                // B: 4문제 중 2개 정답(50%) → 약점 후보
                recordAt(4, NOW, true, "B"), recordAt(5, NOW, true, "B"),
                recordAt(6, NOW, false, "B"), recordAt(7, NOW, false, "B"),
                // C: 2문제 모두 오답(0%)이지만 3문제 미만이라 판단하지 않음
                recordAt(8, NOW, false, "C"), recordAt(9, NOW, false, "C")));
        when(topicRepository.findByName(anyString())).thenReturn(Optional.empty());

        assertThat(service.getStats(1L, "ALL").getWeakCategory()).isEqualTo("A");
    }
}
