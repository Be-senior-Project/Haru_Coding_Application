package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.ProblemEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProblemEmbeddingRepository extends JpaRepository<ProblemEmbedding, Long> {

    /**
     * 임베딩 upsert. 문자열 리터럴을 명시적으로 vector로 CAST해 insert한다.
     * (JPA save()는 varchar→vector 암묵 캐스팅이 안 돼 실패하므로 네이티브로 처리)
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO problem_embeddings (problem_id, embedding)
        VALUES (:problemId, CAST(:vec AS vector))
        ON CONFLICT (problem_id) DO UPDATE SET embedding = CAST(:vec AS vector)
        """, nativeQuery = true)
    void upsertEmbedding(@Param("problemId") Long problemId, @Param("vec") String vec);

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
