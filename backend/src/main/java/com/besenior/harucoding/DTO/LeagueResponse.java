package com.besenior.harucoding.DTO;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LeagueResponse {

    private String myTier;
    private int myRank;
    private int myScore;
    private int season;
    private List<LeagueMember> members;

    @Getter
    @Builder
    public static class LeagueMember {
        private Long userId;
        private String nickname;
        private String tier;
        private int score;
        private int rank;
        private boolean isMe;
    }
}
