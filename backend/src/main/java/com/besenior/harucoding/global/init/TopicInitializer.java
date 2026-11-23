package com.besenior.harucoding.global.init;

import com.besenior.harucoding.entity.Topic;
import com.besenior.harucoding.global.enums.ProblemCategory;
import com.besenior.harucoding.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버가 시작될 때 문제 분류 3개의 주제(topics)를 준비한다.
 * - 없으면 추가하고, 예전에 풀이 제출 때 자동으로 만들어져 아이콘·설명이 비어 있으면 채운다.
 * - 예전 한국어 주제(알고리즘·자료구조 등)는 지우지 않는다. 문제 분류와 이름이 달라 쓰이지 않고, /api/topics에도 나오지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TopicInitializer implements ApplicationRunner {

    private final TopicRepository topicRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ProblemCategory c : ProblemCategory.values()) {
            topicRepository.findByName(c.getKey()).ifPresentOrElse(
                    topic -> topic.fillIfEmpty(c.getDescription(), c.getIcon()),
                    () -> {
                        topicRepository.save(Topic.builder()
                                .name(c.getKey()).description(c.getDescription()).icon(c.getIcon()).build());
                        log.info("주제 추가: {}", c.getKey());
                    });
        }
    }
}
