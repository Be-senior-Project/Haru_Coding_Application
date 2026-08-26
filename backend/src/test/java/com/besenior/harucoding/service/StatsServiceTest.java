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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** STAT-002: 기간 탭(WEEK/MONTH/YEAR/ALL)이 실제로 다른 집계를 돌려주는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProblemRecordRepository recordRepository;
    @Mock private UserStreakLogRepository streakLogRepository;
    @Mock private TopicRepository topicRepository;

    private StatsService service;

    private UserProblemRecord recordAt(LocalDateTime solvedAt, boolean correct, String category) {
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

    @Test
    void 기간별로_풀이_수_집계가_달라진다() {
        service = new StatsService(userRepository, recordRepository, streakLogRepository, topicRepository);

        LocalDateTime now = LocalDateTime.of(LocalDate.now(), java.time.LocalTime.NOON);
        List<UserProblemRecord> records = List.of(
                recordAt(now, true, "알고리즘"),                       // 오늘 → 주간/월간/연간/전체 모두 포함
                recordAt(now.minusMonths(2), true, "알고리즘"),         // 이번 달 밖, 올해 안 → 연간/전체만
                recordAt(now.minusYears(2), false, "알고리즘")          // 작년 → 전체만
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(records);
        when(topicRepository.findByName("알고리즘")).thenReturn(Optional.empty());
        when(streakLogRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

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
        service = new StatsService(userRepository, recordRepository, streakLogRepository, topicRepository);

        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(recordRepository.findByUserIdOrderBySolvedAtDesc(1L)).thenReturn(List.of());
        when(streakLogRepository.findLatestByUserId(1L)).thenReturn(Optional.empty());

        StatsResponse res = service.getStats(1L, "WEEK");

        assertThat(res.getTotalSolved()).isZero();
        assertThat(res.getAccuracyRate()).isZero();
        assertThat(res.getCategoryStats()).isEmpty();
    }
}
