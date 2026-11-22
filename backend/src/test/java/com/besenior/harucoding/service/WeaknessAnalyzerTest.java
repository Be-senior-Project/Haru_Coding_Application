package com.besenior.harucoding.service;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.entity.UserProblemRecord;
import com.besenior.harucoding.global.enums.ProblemType;
import com.besenior.harucoding.service.WeaknessAnalyzer.Weakness;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 공용 약점 기준: 문제별 첫 제출 기준, 3문제 이상, 정답률 70% 미만, 약점 분류 안의 가장 약한 하위 분류. */
class WeaknessAnalyzerTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 20, 12, 0);
    private int clock = 0;

    private UserProblemRecord record(long problemId, String category, String subcategory, boolean correct) {
        Problem p = Problem.builder()
                .type(ProblemType.IMPLEMENTATION).category(category).subcategory(subcategory)
                .difficulty(0).language("Python").title("문제").description("설명")
                .answer("pass").explanation("해설").build();
        ReflectionTestUtils.setField(p, "id", problemId);
        UserProblemRecord r = UserProblemRecord.builder().problem(p).isCorrect(correct).timeSpentSec(10).build();
        ReflectionTestUtils.setField(r, "solvedAt", T0.plusMinutes(clock++));
        return r;
    }

    /** 저장소처럼 최신순으로 정렬해 넘긴다. */
    private static List<UserProblemRecord> newestFirst(List<UserProblemRecord> records) {
        List<UserProblemRecord> list = new ArrayList<>(records);
        list.sort(Comparator.comparing(UserProblemRecord::getSolvedAt).reversed());
        return list;
    }

    @Test
    void 세_문제_이상_풀고_정답률_70퍼센트_미만인_분류를_약점으로_고른다() {
        Weakness w = WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Basic/Introductory", null, true),
                record(2, "Basic/Introductory", null, false),
                record(3, "Basic/Introductory", null, false))));

        assertThat(w).isEqualTo(new Weakness("Basic/Introductory", null));
        assertThat(w.area()).isEqualTo("Basic/Introductory");
    }

    @Test
    void 다시_맞힌_기록은_정답률에_넣지_않는다() {
        // 1~3번을 처음에 모두 틀리고 나중에 다시 맞힘 → 첫 제출 기준 0%라 여전히 약점
        Weakness w = WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Basic/Introductory", null, false),
                record(2, "Basic/Introductory", null, false),
                record(3, "Basic/Introductory", null, false),
                record(1, "Basic/Introductory", null, true),
                record(2, "Basic/Introductory", null, true),
                record(3, "Basic/Introductory", null, true))));

        assertThat(w).isNotNull();
    }

    @Test
    void 약점_분류_안에서_정답률이_가장_낮은_하위_분류를_함께_고른다() {
        Weakness w = WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Algorithm/Data Structure", "Greedy", true),
                record(2, "Algorithm/Data Structure", "Greedy", false),
                record(3, "Algorithm/Data Structure", "DFS/BFS", false),
                record(4, "Algorithm/Data Structure", "DFS/BFS", false))));

        assertThat(w).isEqualTo(new Weakness("Algorithm/Data Structure", "DFS/BFS"));
        assertThat(w.area()).isEqualTo("DFS/BFS");
    }

    @Test
    void 기준을_만족하는_분류가_없으면_약점이_없다() {
        // 두 문제뿐(3문제 미만) / 정답률 75%(70% 이상)
        assertThat(WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Basic/Introductory", null, false),
                record(2, "Basic/Introductory", null, false))))).isNull();
        assertThat(WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Basic/Introductory", null, true),
                record(2, "Basic/Introductory", null, true),
                record(3, "Basic/Introductory", null, true),
                record(4, "Basic/Introductory", null, false))))).isNull();
    }

    @Test
    void 여러_분류가_기준을_만족하면_정답률이_가장_낮은_분류를_고른다() {
        Weakness w = WeaknessAnalyzer.find(newestFirst(List.of(
                record(1, "Basic/Introductory", null, true),
                record(2, "Basic/Introductory", null, false),
                record(3, "Basic/Introductory", null, false),
                record(4, "Past Exam Prep", "Kakao", false),
                record(5, "Past Exam Prep", "Kakao", false),
                record(6, "Past Exam Prep", "General", false))));

        assertThat(w.category()).isEqualTo("Past Exam Prep");
        assertThat(w.subcategory()).isEqualTo("Kakao");   // 같은 0%면 더 많이 푼 하위 분류
    }
}
