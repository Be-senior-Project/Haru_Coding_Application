package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.RecommendationFilterDto;
import com.besenior.harucoding.DTO.UserProfileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 온보딩(신규 유저) 추천 전용.
 * - coding_level / cote_prepared 기반 난이도·이유·집중포인트 산출 (전부 규칙 기반)
 * - 조합이 6가지(코딩 경험 3 × 코테 준비 2)뿐이라 문구를 미리 정해두고 GPT 호출은 하지 않는다.
 *   매번 같은 6개 중 하나가 나올 내용이라 API 비용·응답 지연만 늘고 얻는 게 없었다.
 * - 기존 유저 개인화 추천은 ProblemRecommendationService(/api/recommendations)로 대체됨
 *
 * 저장은 하지 않는다(계산 전용). 가입 전에도 결과를 보여줘야 해서 userId가 없을 수 있고,
 * 답변을 users에 기록하는 일은 가입이 확정된 뒤 UserService가 맡는다.
 */
@Slf4j
@Service
public class RecommendationService {

    /** 온보딩 결과 화면에 노출할 고정 문구(추천 이유 + 학습 포인트). */
    private record OnboardingMessage(String reason, String focusPoint) {}

    // ── 온보딩 추천 (신규 유저) ────────────────────────────────────
    public RecommendationFilterDto recommendOnboarding(UserProfileDto profile) {
        int score = calcOnboardingScore(profile);
        String difficulty = scoreToDifficulty(score);
        OnboardingMessage message = onboardingMessage(profile.getCodingLevel(), profile.isCotePrepared());

        return RecommendationFilterDto.builder()
                .difficulty(difficulty)
                .topicIds(List.of(1))
                .type("객관식")
                .style("일반")
                .language(profile.getPreferredLanguage() != null
                        ? profile.getPreferredLanguage() : "COMMON")
                .reason(message.reason())
                .focusPoint(message.focusPoint())
                .method("rule_based")
                .build();
    }

    /**
     * 온보딩 답변만으로 문제 난이도(0/1/2)를 계산한다.
     * recommended_difficulty 컬럼에 저장해두고 읽던 것을 대신하는 단일 규칙 —
     * 원본 답변(coding_level, cote_prepared)이 있으면 언제든 다시 구할 수 있어 저장하지 않는다.
     */
    public int onboardingBaseLevel(String codingLevel, boolean cotePrepared) {
        int score = calcScore(codingLevel, cotePrepared);
        if (score < 25) return 0;   // 입문
        if (score < 50) return 1;   // 초급
        return 2;                   // 중급·고급
    }

    // ── 6가지 조합별 고정 문구 ─────────────────────────────────────
    private OnboardingMessage onboardingMessage(String codingLevel, boolean cotePrepared) {
        String level = codingLevel != null ? codingLevel : "NONE";
        return switch (level) {
            case "LOTS" -> cotePrepared
                    ? new OnboardingMessage(
                            "실력이 탄탄하시네요. 까다로운 유형으로 실전 감각을 끌어올려 봅시다.",
                            "DP와 그래프 탐색 정복하기")
                    : new OnboardingMessage(
                            "코딩은 익숙하시니 이제 코테 유형에 적응하는 게 관건입니다.",
                            "시간복잡도 계산과 자료구조 선택");
            case "SOME" -> cotePrepared
                    ? new OnboardingMessage(
                            "기본기와 코테 경험이 모두 있으니 자료구조를 본격적으로 다뤄볼 때입니다.",
                            "스택, 큐, 해시 활용하기")
                    : new OnboardingMessage(
                            "코딩 경험이 있으니 문법은 가볍게 넘기고 문제 풀이 감각을 키워봐요.",
                            "완전탐색과 정렬 익히기");
            default -> cotePrepared
                    ? new OnboardingMessage(
                            "코테를 준비해보셨군요. 기초를 빠르게 다지고 바로 문제 풀이로 들어가 봅시다.",
                            "배열과 문자열 기본 다루기")
                    : new OnboardingMessage(
                            "코딩이 처음이시군요! 기초 문법부터 차근차근 시작하면 충분합니다.",
                            "변수, 조건문, 반복문 익히기");
        };
    }

    // ── 점수 계산 ──────────────────────────────────────────────────
    private int calcOnboardingScore(UserProfileDto profile) {
        return calcScore(profile.getCodingLevel(), profile.isCotePrepared());
    }

    private int calcScore(String codingLevel, boolean cotePrepared) {
        int score = switch (codingLevel != null ? codingLevel : "NONE") {
            case "LOTS" -> 60;
            case "SOME" -> 30;
            default     -> 0;
        };
        if (cotePrepared) score += 40;
        return score;
    }

    private String scoreToDifficulty(int score) {
        if (score < 25)  return "입문";
        if (score < 50)  return "초급";
        if (score < 75)  return "중급";
        return "고급";
    }
}
