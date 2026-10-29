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

    @Modifying
    @Query("delete from OtpCode o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
