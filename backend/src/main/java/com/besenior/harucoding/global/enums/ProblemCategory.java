package com.besenior.harucoding.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문제 분류 3개(문제 생성 프롬프트 generation_en.md 기준). problems.category와 topics.name이 이 key를 같이 쓴다.
 * 화면에 보일 한국어 이름은 앱(types/problem.ts의 CATEGORY_LABEL)에서 관리한다.
 * 순서가 홈 "추천 학습 주제" 카드 순서다.
 */
@Getter
@RequiredArgsConstructor
public enum ProblemCategory {
    BASIC("Basic/Introductory", "변수·조건문·반복문 등 기초 문법", "code"),
    ALGORITHM("Algorithm/Data Structure", "정렬·탐색·그래프·DP 등 알고리즘과 자료구조", "account-tree"),
    PAST_EXAM("Past Exam Prep", "카카오·대회 등 기출 유형", "assignment");

    private final String key;
    private final String description;
    private final String icon;
}
