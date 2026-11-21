package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserProblemRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserProblemRecordRepository extends JpaRepository<UserProblemRecord, Long> {

    List<UserProblemRecord> findByUserIdOrderBySolvedAtDesc(Long userId);

    /** 이 문제를 이미 맞힌 적이 있는지. XP는 문제별 첫 정답에만 준다. */
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM UserProblemRecord r
        WHERE r.user.id = :userId
        AND r.problem.id = :problemId
        AND r.isCorrect = true
        """)
    boolean existsCorrect(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /** 첫 제출에서 맞힌 문제 수. 같은 문제를 다시 제출한 기록은 세지 않는다(학습 통계와 같은 기준). */
    @Query("""
        SELECT COUNT(r) FROM UserProblemRecord r
        WHERE r.user.id = :userId
        AND r.isCorrect = true
        AND r.id = (SELECT MIN(r2.id) FROM UserProblemRecord r2
                    WHERE r2.user.id = r.user.id AND r2.problem.id = r.problem.id)
        """)
    long countCorrectByUserId(@Param("userId") Long userId);

    /** 푼 문제 수(서로 다른 문제 수). */
    @Query("""
        SELECT COUNT(DISTINCT r.problem.id) FROM UserProblemRecord r
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

    void deleteAllByUserId(Long userId);
}
