package com.cognologix.fpa.general.repository;

import com.cognologix.fpa.general.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query("""
            select count(a) from LoginAttempt a
            where lower(a.email) = lower(:email)
              and a.attemptedAt >= :since
            """)
    long countByEmailSince(@Param("email") String email, @Param("since") Instant since);
}
