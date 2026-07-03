package com.gabrielbicu.telemetry.repository;

import com.gabrielbicu.telemetry.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used by the auth flow: look up a user by email at login. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
