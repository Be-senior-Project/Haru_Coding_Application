package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.ProblemSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface ProblemSetRepository extends JpaRepository<ProblemSet, Long> {

    Optional<ProblemSet> findByTargetDate(LocalDate targetDate);

    @Query("""
        SELECT ps FROM ProblemSet ps
        LEFT JOIN FETCH ps.items psi
        LEFT JOIN FETCH psi.problem p
        LEFT JOIN FETCH p.topic
        WHERE ps.targetDate = :date
        """)
    Optional<ProblemSet> findByTargetDateWithItems(@Param("date") LocalDate date);

    @Query("""
        SELECT ps FROM ProblemSet ps
        LEFT JOIN FETCH ps.items psi
        LEFT JOIN FETCH psi.problem p
        LEFT JOIN FETCH p.topic
        WHERE ps.id = :id
        """)
    Optional<ProblemSet> findByIdWithItems(@Param("id") Long id);
}