package com.besenior.harucoding.service;

import com.besenior.harucoding.DTO.LeagueResponse;
import com.besenior.harucoding.entity.User;
import com.besenior.harucoding.entity.UserLeague;
import com.besenior.harucoding.global.enums.LeagueTier;
import com.besenior.harucoding.global.exception.CustomException;
import com.besenior.harucoding.global.exception.ErrorCode;
import com.besenior.harucoding.repository.UserLeagueRepository;
import com.besenior.harucoding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private static final int CURRENT_SEASON = 1;

    private final UserLeagueRepository leagueRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeagueResponse getMyLeague(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserLeague myLeague = leagueRepository.findByUserIdAndSeason(userId, CURRENT_SEASON)
                .orElseGet(() -> assignLeague(user));

        List<UserLeague> allInTier = leagueRepository
                .findByTierAndSeasonOrderByScoreDesc(myLeague.getTier(), CURRENT_SEASON);

        List<LeagueResponse.LeagueMember> members = new ArrayList<>();
        int myRank = 0;
        for (int i = 0; i < allInTier.size(); i++) {
            UserLeague l = allInTier.get(i);
            int rank = i + 1;
            boolean isMe = l.getUser().getId().equals(userId);
            if (isMe) myRank = rank;
            members.add(LeagueResponse.LeagueMember.builder()
                    .userId(l.getUser().getId())
                    .nickname(l.getUser().getNickname())
                    .tier(l.getTier().name())
                    .score(l.getScore())
                    .rank(rank)
                    .isMe(isMe)
                    .build());
        }

        return LeagueResponse.builder()
                .myTier(myLeague.getTier().name())
                .myRank(myRank)
                .myScore(myLeague.getScore())
                .season(CURRENT_SEASON)
                .members(members)
                .build();
    }

    /** 문제 풀이 시 리그 점수 갱신 */
    @Transactional
    public void addScore(Long userId, int xpEarned) {
        leagueRepository.findByUserIdAndSeason(userId, CURRENT_SEASON)
                .ifPresent(league -> league.addScore(xpEarned));
    }

    private UserLeague assignLeague(User user) {
        LeagueTier tier = tierFromLevel(user.getLevel());
        UserLeague league = UserLeague.builder()
                .user(user)
                .tier(tier)
                .score(0)
                .season(CURRENT_SEASON)
                .build();
        return leagueRepository.save(league);
    }

    private LeagueTier tierFromLevel(int level) {
        if (level >= 20) return LeagueTier.DIAMOND;
        if (level >= 15) return LeagueTier.PLATINUM;
        if (level >= 10) return LeagueTier.GOLD;
        if (level >= 5) return LeagueTier.SILVER;
        return LeagueTier.BRONZE;
    }
}
