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

    /**
     * 오답노트용. 틀린 기록만 최신순으로 가져온다.
     * 같은 문제를 여러 번 틀렸으면 행도 여러 개 나오므로, 문제 단위 중복 제거는 서비스에서 한다.
     */
    @Query("""
        SELECT r FROM UserProblemRecord r
        JOIN FETCH r.problem
        WHERE r.user.id = :userId
        AND r.isCorrect = false
        ORDER BY r.solvedAt DESC
        """)
    List<UserProblemRecord> findWrongRecordsByUserId(@Param("userId") Long userId);
}
