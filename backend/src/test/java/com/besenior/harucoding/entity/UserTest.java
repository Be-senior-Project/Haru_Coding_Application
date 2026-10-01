package com.besenior.harucoding.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** MY-018/MY-019: 하루 목표 문제 수·난이도 조절 필드의 부분 업데이트 규칙. */
class UserTest {

    private User newUser() {
        return User.builder().email("t@t.com").emailHash("h").nickname("tester").build();
    }

    @Test
    void 기본_하루_목표는_3문제다() {
        assertThat(newUser().getDailyGoalCount()).isEqualTo(3);
    }

    @Test
    void 하루_목표_문제_수만_바꾸면_다른_필드는_그대로다() {
        User user = newUser();
        user.updateProfile(null, "PYTHON", null, null, null);

        user.updateProfile(null, null, null, 7, null);

        assertThat(user.getDailyGoalCount()).isEqualTo(7);
        assertThat(user.getPreferredLanguage()).isEqualTo("PYTHON");
    }

    @Test
    void 난이도를_L3로_지정할_수_있다() {
        User user = newUser();
        user.updateProfile(null, null, null, null, "L3");
        assertThat(user.getDifficultyLevel()).isEqualTo("L3");
    }

    @Test
    void AUTO를_보내면_난이도_지정이_해제된다() {
        User user = newUser();
        user.updateProfile(null, null, null, null, "L3");
        assertThat(user.getDifficultyLevel()).isEqualTo("L3");

        user.updateProfile(null, null, null, null, "AUTO");

        assertThat(user.getDifficultyLevel()).isNull();
    }

    @Test
    void 난이도를_생략하면_기존_값이_유지된다() {
        User user = newUser();
        user.updateProfile(null, null, null, null, "L4");

        user.updateProfile("새닉네임", null, null, null, null);

        assertThat(user.getDifficultyLevel()).isEqualTo("L4");
        assertThat(user.getNickname()).isEqualTo("새닉네임");
    }
}
