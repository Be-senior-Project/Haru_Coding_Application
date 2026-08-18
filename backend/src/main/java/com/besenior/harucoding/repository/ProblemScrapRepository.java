package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.ProblemScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemScrapRepository extends JpaRepository<ProblemScrap, Long> {

    /** 목록 조회. 문제까지 함께 가져와 N+1을 피한다. */
    @Query("""
        SELECT s FROM ProblemScrap s
        JOIN FETCH s.problem
        WHERE s.user.id = :userId
        ORDER BY s.createdAt DESC
        """)
    List<ProblemScrap> findAllByUserId(@Param("userId") Long userId);

    Optional<ProblemScrap> findByUserIdAndProblemId(Long userId, Long problemId);

    boolean existsByUserIdAndProblemId(Long userId, Long problemId);
}
