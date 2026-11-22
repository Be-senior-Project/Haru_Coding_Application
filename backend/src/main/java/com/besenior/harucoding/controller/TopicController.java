package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.TopicResponse;
import com.besenior.harucoding.global.enums.ProblemCategory;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicRepository topicRepository;

    /** 문제 분류 3개의 주제만 정해진 순서로 돌려준다(예전 한국어 주제는 문제 분류와 이름이 달라 제외). */
    @GetMapping
    public ApiResponse<List<TopicResponse>> getTopics() {
        List<TopicResponse> topics = Arrays.stream(ProblemCategory.values())
                .map(c -> topicRepository.findByName(c.getKey()))
                .flatMap(Optional::stream)
                .map(TopicResponse::from)
                .toList();
        return ApiResponse.success(topics);
    }
}
