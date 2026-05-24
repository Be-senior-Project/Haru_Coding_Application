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
}
