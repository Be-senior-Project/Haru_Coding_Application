package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserProblemRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserProblemRecordRepository extends JpaRepository<UserProblemRecord, Long> {

    List<UserProblemRecord> findByUserIdOrderBySolvedAtDesc(Long userId);

    @Query("""
        SELECT COUNT(r) FROM UserProblemRecord r
        WHERE r.user.id = :userId
        AND r.isCorrect = true
        """)
    long countCorrectByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(r) FROM UserProblemRecord r
        WHERE r.user.id = :userId
        """)
    long countTotalByUserId(@Param("userId") Long userId);
}
