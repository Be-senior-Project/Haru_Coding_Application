package com.besenior.harucoding.global.enums;

/**
 * 세트 난이도(problem_sets.difficulty에 이름 그대로 저장).
 * 지금은 입문·초급·중급만 만든다(DifficultyRules.toSetLevel).
 * 고급은 이미 저장된 값이 있을 수 있어 지우지 않는다(지우면 그 행을 읽을 때 오류가 난다).
 */
public enum DifficultyLevel {
    입문, 초급, 중급, 고급
}
