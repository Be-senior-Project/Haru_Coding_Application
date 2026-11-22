package com.besenior.harucoding.global.util;

import com.besenior.harucoding.global.enums.DifficultyLevel;

/**
 * 난이도 표현 사이의 변환 규칙을 한 곳에 모은다.
 * 기준값은 문제 난이도 0·1·2(입문·초급·중급 = 프로그래머스 Lv.0~2)이고, 다른 표현은 모두 여기서 바꾼다.
 * - 온보딩 답변(코딩 경험·코테 준비 경험) → 0/1/2
 * - 프로필 난이도 설정 L1~L5 → 0/1/2 (앱은 입문·초급·중급을 L1·L3·L5로 저장한다. L2·L4는 예전에 저장된 값)
 * - 0/1/2 → 표시 이름, 세트 난이도(DifficultyLevel)
 */
public final class DifficultyRules {

    private static final String[] LABELS = {"입문", "초급", "중급"};

    private DifficultyRules() {
    }

    /** 0/1/2 → 입문·초급·중급 */
    public static String label(int level) {
        return LABELS[clamp(level)];
    }

    /** 온보딩 답변 → 0/1/2. 코딩 경험(많이 60·조금 30·처음 0)과 코테 준비 경험(40)의 점수로 나눈다. */
    public static int fromOnboarding(String codingLevel, boolean cotePrepared) {
        int score = switch (codingLevel != null ? codingLevel : "NONE") {
            case "LOTS" -> 60;
            case "SOME" -> 30;
            default -> 0;
        };
        if (cotePrepared) score += 40;
        if (score < 25) return 0;
        if (score < 50) return 1;
        return 2;
    }

    /** 프로필 난이도 설정 → 0/1/2. 자동(null·AUTO)이면 null. */
    public static Integer fromUserSetting(String setting) {
        if (setting == null) return null;
        return switch (setting) {
            case "L1" -> 0;
            case "L2", "L3" -> 1;
            case "L4", "L5" -> 2;
            default -> null;
        };
    }

    /** 0/1/2 → 세트 난이도 */
    public static DifficultyLevel toSetLevel(int level) {
        return switch (clamp(level)) {
            case 1 -> DifficultyLevel.초급;
            case 2 -> DifficultyLevel.중급;
            default -> DifficultyLevel.입문;
        };
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(2, level));
    }
}
