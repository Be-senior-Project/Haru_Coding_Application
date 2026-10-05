package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserLeague;
import com.besenior.harucoding.global.enums.LeagueTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserLeagueRepository extends JpaRepository<UserLeague, Long> {

    Optional<UserLeague> findByUserIdAndSeason(Long userId, int season);

    @Query("""
        SELECT l FROM UserLeague l
        JOIN FETCH l.user
        WHERE l.tier = :tier AND l.season = :season
        ORDER BY l.score DESC
        """)
    List<UserLeague> findByTierAndSeasonOrderByScoreDesc(
            @Param("tier") LeagueTier tier, @Param("season") int season);

    @Query("""
        SELECT l FROM UserLeague l
        JOIN FETCH l.user
        WHERE l.tier = :tier AND l.season = :season AND l.groupId = :groupId
        ORDER BY l.score DESC
        """)
    List<UserLeague> findByTierAndSeasonAndGroupId(
            @Param("tier") LeagueTier tier, @Param("season") int season, @Param("groupId") int groupId);

    @Query("""
        SELECT l FROM UserLeague l
        WHERE l.tier = :tier AND l.season = :season AND l.groupId IS NULL
        """)
    List<UserLeague> findUnassigned(
            @Param("tier") LeagueTier tier, @Param("season") int season);

    @Query("SELECT COALESCE(MAX(l.groupId), 0) FROM UserLeague l WHERE l.tier = :tier AND l.season = :season")
    int findMaxGroupId(@Param("tier") LeagueTier tier, @Param("season") int season);

    void deleteAllByUserId(Long userId);
}
