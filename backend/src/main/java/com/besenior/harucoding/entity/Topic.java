package com.besenior.harucoding.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private String icon;

    @Builder
    public Topic(String name, String description, String icon) {
        this.name = name;
        this.description = description;
        this.icon = icon;
    }

    /** 비어 있는 설명·아이콘만 채운다(이미 있는 값은 그대로 둔다). */
    public void fillIfEmpty(String description, String icon) {
        if (this.description == null || this.description.isBlank()) {
            this.description = description;
        }
        if (this.icon == null || this.icon.isBlank()) {
            this.icon = icon;
        }
    }
}
