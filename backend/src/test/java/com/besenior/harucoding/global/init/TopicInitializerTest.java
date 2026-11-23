package com.besenior.harucoding.global.init;

import com.besenior.harucoding.controller.TopicController;
import com.besenior.harucoding.DTO.TopicResponse;
import com.besenior.harucoding.entity.Topic;
import com.besenior.harucoding.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 분류 3개의 주제 준비(TopicInitializer)와 주제 목록(/api/topics)이 문제 분류 기준으로 맞는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class TopicInitializerTest {

    @Mock private TopicRepository topicRepository;

    @Test
    void 없는_분류는_추가하고_아이콘이_비어_있으면_채운다() {
        Topic autoCreated = Topic.builder().name("Algorithm/Data Structure").build();          // 예전에 자동 생성(아이콘 없음)
        Topic existing = Topic.builder().name("Past Exam Prep").icon("star").description("기존 설명").build();
        when(topicRepository.findByName("Basic/Introductory")).thenReturn(Optional.empty());
        when(topicRepository.findByName("Algorithm/Data Structure")).thenReturn(Optional.of(autoCreated));
        when(topicRepository.findByName("Past Exam Prep")).thenReturn(Optional.of(existing));

        new TopicInitializer(topicRepository).run(null);

        ArgumentCaptor<Topic> saved = ArgumentCaptor.forClass(Topic.class);
        verify(topicRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Basic/Introductory");
        assertThat(saved.getValue().getIcon()).isEqualTo("code");
        assertThat(autoCreated.getIcon()).isEqualTo("account-tree");
        assertThat(existing.getIcon()).isEqualTo("star");                 // 이미 있는 값은 그대로
    }

    @Test
    void 주제_목록은_분류_3개만_정해진_순서로_돌려준다() {
        when(topicRepository.findByName("Basic/Introductory"))
                .thenReturn(Optional.of(Topic.builder().name("Basic/Introductory").build()));
        when(topicRepository.findByName("Algorithm/Data Structure")).thenReturn(Optional.empty());
        when(topicRepository.findByName("Past Exam Prep"))
                .thenReturn(Optional.of(Topic.builder().name("Past Exam Prep").build()));

        List<TopicResponse> topics = new TopicController(topicRepository).getTopics().getData();

        // 예전 한국어 주제(알고리즘·자료구조 등)는 조회하지 않으므로 나오지 않는다
        assertThat(topics).extracting(TopicResponse::getName).containsExactly("Basic/Introductory", "Past Exam Prep");
    }
}
