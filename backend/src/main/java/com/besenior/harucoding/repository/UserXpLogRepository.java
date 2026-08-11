package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserXpLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserXpLogRepository extends JpaRepository<UserXpLog, Long> {

    List<UserXpLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(x.xpAmount), 0) FROM UserXpLog x WHERE x.user.id = :userId")
    int sumXpByUserId(@Param("userId") Long userId);
}
