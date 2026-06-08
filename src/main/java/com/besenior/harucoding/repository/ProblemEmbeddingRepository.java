package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.ProblemEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemEmbeddingRepository extends JpaRepository<ProblemEmbedding, Long> {

    /**
     * 약점 벡터(:vec)와 코사인 거리가 가까운 문제 후보를 ANN(HNSW)으로 검색한다.
     * - 이미 마스터한 문제(:excludeIds)는 제외
     * - 선호 언어 필터 (:lang 이 빈 문자열이면 전체 언어 허용)
     * - 난이도 밴드 [:lo, :hi] 내에서만
     */
    @Query(value = """
        SELECT p.id
        FROM problems p
        JOIN problem_embeddings e ON e.problem_id = p.id
        WHERE p.id NOT IN (:excludeIds)
          AND (:lang = '' OR p.language = :lang)
          AND p.difficulty BETWEEN :lo AND :hi
        ORDER BY e.embedding <=> CAST(:vec AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findNearestCandidateIds(@Param("vec") String vec,
                                       @Param("excludeIds") List<Long> excludeIds,
                                       @Param("lang") String lang,
                                       @Param("lo") int lo,
                                       @Param("hi") int hi,
                                       @Param("limit") int limit);
}
