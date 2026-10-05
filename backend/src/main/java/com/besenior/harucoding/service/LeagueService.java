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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private static final int CURRENT_SEASON = 1;
    private static final int DISPLAY_SIZE = 10;

    private final UserLeagueRepository leagueRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeagueResponse getMyLeague(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserLeague myLeague = leagueRepository.findByUserIdAndSeason(userId, CURRENT_SEASON)
                .orElseGet(() -> assignLeague(user));
        myLeague.syncScore(user.getXp());

        List<UserLeague> allInTier = leagueRepository
                .findByTierAndSeasonOrderByScoreDesc(myLeague.getTier(), CURRENT_SEASON);

        int myRank = 0;
        int myIdx = -1;
        for (int i = 0; i < allInTier.size(); i++) {
            if (allInTier.get(i).getUser().getId().equals(userId)) {
                myRank = i + 1;
                myIdx = i;
                break;
            }
        }

        // 상위 3명 + 내 순위 근처를 합쳐서 최대 DISPLAY_SIZE명
        Map<Integer, UserLeague> picked = new LinkedHashMap<>();

        // 상위 3명
        for (int i = 0; i < Math.min(3, allInTier.size()); i++) {
            picked.put(i, allInTier.get(i));
        }

        // 내 순위 근처: 남은 슬롯만큼 위아래로 확장
        if (myIdx >= 0) {
            int remaining = DISPLAY_SIZE - picked.size();
            int above = myIdx - remaining / 2;
            int below = myIdx + remaining / 2;

            if (above < 0) {
                below = Math.min(below - above, allInTier.size() - 1);
                above = 0;
            }
            if (below >= allInTier.size()) {
                above = Math.max(above - (below - allInTier.size() + 1), 0);
                below = allInTier.size() - 1;
            }

            for (int i = above; i <= below; i++) {
                picked.putIfAbsent(i, allInTier.get(i));
            }
        }

        List<LeagueResponse.LeagueMember> members = new ArrayList<>();
        for (Map.Entry<Integer, UserLeague> entry : picked.entrySet()) {
            int rank = entry.getKey() + 1;
            UserLeague l = entry.getValue();
            boolean isMe = l.getUser().getId().equals(userId);
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
                .totalMembers(allInTier.size())
                .members(members)
                .build();
    }

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
