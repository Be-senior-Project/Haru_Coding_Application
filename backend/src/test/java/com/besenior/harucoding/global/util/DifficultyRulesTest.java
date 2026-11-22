package com.besenior.harucoding.global.util;

import com.besenior.harucoding.global.enums.DifficultyLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 난이도 변환 규칙(온보딩 답변·프로필 설정·세트 난이도)이 한 기준(0/1/2)으로 맞는지 검증한다. */
class DifficultyRulesTest {

    @Test
    void 온보딩_답변_6가지_조합을_입문_초급_중급으로_나눈다() {
        assertThat(DifficultyRules.fromOnboarding("NONE", false)).isZero();
        assertThat(DifficultyRules.fromOnboarding("NONE", true)).isEqualTo(1);
        assertThat(DifficultyRules.fromOnboarding("SOME", false)).isEqualTo(1);
        assertThat(DifficultyRules.fromOnboarding("SOME", true)).isEqualTo(2);
        assertThat(DifficultyRules.fromOnboarding("LOTS", false)).isEqualTo(2);
        assertThat(DifficultyRules.fromOnboarding("LOTS", true)).isEqualTo(2);
        assertThat(DifficultyRules.fromOnboarding(null, false)).isZero();
    }

    @Test
    void 프로필_설정은_L1_입문_L2L3_초급_L4L5_중급이고_자동이면_null() {
        assertThat(DifficultyRules.fromUserSetting("L1")).isZero();
        assertThat(DifficultyRules.fromUserSetting("L2")).isEqualTo(1);
        assertThat(DifficultyRules.fromUserSetting("L3")).isEqualTo(1);
        assertThat(DifficultyRules.fromUserSetting("L4")).isEqualTo(2);
        assertThat(DifficultyRules.fromUserSetting("L5")).isEqualTo(2);
        assertThat(DifficultyRules.fromUserSetting(null)).isNull();
        assertThat(DifficultyRules.fromUserSetting("AUTO")).isNull();
    }

    @Test
    void 표시_이름과_세트_난이도() {
        assertThat(DifficultyRules.label(0)).isEqualTo("입문");
        assertThat(DifficultyRules.label(2)).isEqualTo("중급");
        assertThat(DifficultyRules.toSetLevel(1)).isEqualTo(DifficultyLevel.초급);
        assertThat(DifficultyRules.toSetLevel(5)).isEqualTo(DifficultyLevel.중급);   // 범위를 넘으면 가장 가까운 값
    }
}
