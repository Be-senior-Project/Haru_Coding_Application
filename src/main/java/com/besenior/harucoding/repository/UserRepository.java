package com.besenior.harucoding.repository;

import com.besenior.harucoding.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    boolean existsByGoogleId(String googleId);

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);
}
