package com.besenior.harucoding.generation.seed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SeedSetRepository extends JpaRepository<SeedSet, Long> {

    /** 벡터 임베딩을 명시적 CAST로 갱신(varchar→vector 암묵 캐스팅 불가 대응). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE seed_sets SET embedding = CAST(:vec AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("vec") String vec);

    /**
     * 요청 조건(category/subcategory/language/difficulty)으로 필터 후
     * 개념 임베딩(:vec)과 코사인 거리가 가까운 시드 세트 top-k 반환.
     * subcategory가 null이면(예: Basic) category 필터만으로 충분하다.
     */
    @Query(value = """
        SELECT * FROM seed_sets
        WHERE category = :category
          AND language = :language
          AND difficulty = :difficulty
          AND (CAST(:subcategory AS varchar) IS NULL OR subcategory = :subcategory)
        ORDER BY embedding <=> CAST(:vec AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<SeedSet> findNearest(@Param("category") String category,
                              @Param("subcategory") String subcategory,
                              @Param("language") String language,
                              @Param("difficulty") int difficulty,
                              @Param("vec") String vec,
                              @Param("limit") int limit);
}
