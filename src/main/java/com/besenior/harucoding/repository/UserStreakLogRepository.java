package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserStreakLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface UserStreakLogRepository extends JpaRepository<UserStreakLog, Long> {

    Optional<UserStreakLog> findByUserIdAndStreakDate(Long userId, LocalDate date);

    // 가장 최근 스트릭 기록
    @Query("""
        SELECT s FROM UserStreakLog s
        WHERE s.user.id = :userId
        ORDER BY s.streakDate DESC
        LIMIT 1
        """)
    Optional<UserStreakLog> findLatestByUserId(@Param("userId") Long userId);
}
