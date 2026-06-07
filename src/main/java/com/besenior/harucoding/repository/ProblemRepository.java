package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByDifficulty(int difficulty);

    List<Problem> findByCategory(String category);

    List<Problem> findBySetId(String setId);
}
