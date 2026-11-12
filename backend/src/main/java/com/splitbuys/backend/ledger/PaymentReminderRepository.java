package com.splitbuys.backend.ledger;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {
    Optional<PaymentReminder> findFirstByGroup_IdAndSender_IdAndDebtor_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long groupId, Long senderId, Long debtorId, Instant after);

    long countBySender_IdAndCreatedAtAfter(Long senderId, Instant after);

    @Modifying
    @Query("delete from PaymentReminder r where r.group.id = :groupId")
    void deleteByGroupId(Long groupId);
}
