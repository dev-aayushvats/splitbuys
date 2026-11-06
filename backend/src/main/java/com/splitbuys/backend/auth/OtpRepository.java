package com.splitbuys.backend.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface OtpRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("delete from OtpCode o where o.email = :email")
    void deleteByEmail(String email);

    /**
     * Atomically spends one guess: a single UPDATE that only succeeds while attempts remain. Returns 0 when the code
     * is locked. Doing the check and the increment in one statement means parallel guesses cannot exceed the limit.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OtpCode o set o.attempts = o.attempts + 1 where o.id = :id and o.attempts < :max")
    int claimAttempt(long id, int max);

    @Modifying
    @Query("delete from OtpCode o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
