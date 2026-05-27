package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.Problem;
import com.besenior.harucoding.global.enums.DifficultyLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByTopicId(Long topicId);

    List<Problem> findByDifficulty(DifficultyLevel difficulty);

    // pgvector 코사인 유사도 검색 (RAG)
    @Query(value = """
        SELECT * FROM problems
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Problem> findSimilarProblems(@Param("embedding") String embedding,
                                      @Param("limit") int limit);
}
