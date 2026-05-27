package com.besenior.harucoding.DTO;

import com.besenior.harucoding.entity.Topic;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopicResponse {
    private Long id;
    private String name;
    private String description;
    private String icon;

    public static TopicResponse from(Topic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .icon(topic.getIcon())
                .build();
    }
}