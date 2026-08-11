package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.global.enums.ProblemType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByDifficulty(int difficulty);

    List<Problem> findByCategory(String category);

    List<Problem> findBySetId(String setId);

    /** 선택적 필터(null이면 무시) 검색. 문제 은행 목록용. */
    @Query("""
        SELECT p FROM Problem p
        WHERE (:category IS NULL OR p.category = :category)
          AND (:difficulty IS NULL OR p.difficulty = :difficulty)
          AND (:type IS NULL OR p.type = :type)
          AND (:language IS NULL OR p.language = :language)
        ORDER BY p.difficulty ASC, p.id ASC
        """)
    List<Problem> search(@Param("category") String category,
                         @Param("difficulty") Integer difficulty,
                         @Param("type") ProblemType type,
                         @Param("language") String language,
                         Pageable pageable);
}
