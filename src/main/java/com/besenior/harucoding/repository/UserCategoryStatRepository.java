package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.UserCategoryStat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserCategoryStatRepository extends JpaRepository<UserCategoryStat, Long> {

    List<UserCategoryStat> findByUserId(Long userId);

    Optional<UserCategoryStat> findByUserIdAndTopicId(Long userId, Long topicId);
}
