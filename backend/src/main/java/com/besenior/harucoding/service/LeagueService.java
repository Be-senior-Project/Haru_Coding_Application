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
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private static final int CURRENT_SEASON = 1;
    private static final int GROUP_SIZE = 10;

    private final UserLeagueRepository leagueRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeagueResponse getMyLeague(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserLeague myLeague = leagueRepository.findByUserIdAndSeason(userId, CURRENT_SEASON)
                .orElseGet(() -> assignLeague(user));
        myLeague.syncScore(user.getXp());

        if (myLeague.getGroupId() == null) {
            assignGroup(myLeague);
        }

        List<UserLeague> groupMembers = myLeague.getGroupId() != null
                ? leagueRepository.findByTierAndSeasonAndGroupId(myLeague.getTier(), CURRENT_SEASON, myLeague.getGroupId())
                : leagueRepository.findByTierAndSeasonOrderByScoreDesc(myLeague.getTier(), CURRENT_SEASON);

        List<LeagueResponse.LeagueMember> members = new ArrayList<>();
        int myRank = 0;
        for (int i = 0; i < groupMembers.size(); i++) {
            UserLeague l = groupMembers.get(i);
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

    private void assignGroup(UserLeague league) {
        List<UserLeague> unassigned = leagueRepository.findUnassigned(league.getTier(), CURRENT_SEASON);
        if (unassigned.size() >= GROUP_SIZE) {
            Collections.shuffle(unassigned);
            int newGroupId = leagueRepository.findMaxGroupId(league.getTier(), CURRENT_SEASON) + 1;
            for (int i = 0; i < GROUP_SIZE; i++) {
                unassigned.get(i).assignGroup(newGroupId);
            }
        }
    }

    private UserLeague assignLeague(User user) {
        LeagueTier tier = tierFromLevel(user.getLevel());
        UserLeague league = UserLeague.builder()
                .user(user)
                .tier(tier)
                .score(user.getXp())
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
