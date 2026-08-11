package com.besenior.harucoding.controller;

import com.besenior.harucoding.DTO.TopicResponse;
import com.besenior.harucoding.global.response.ApiResponse;
import com.besenior.harucoding.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicRepository topicRepository;

    @GetMapping
    public ApiResponse<List<TopicResponse>> getTopics() {
        List<TopicResponse> topics = topicRepository.findAll().stream()
                .map(TopicResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(topics);
    }
}