package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** A reminder that was sent. Kept for the cooldown and the daily cap, not for display. */
@Entity
class PaymentReminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpenseGroup group;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser sender;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser debtor;

    private long amountPaise;

    private Instant createdAt = Instant.now();

    protected PaymentReminder() {
    }

    PaymentReminder(ExpenseGroup group, AppUser sender, AppUser debtor, long amountPaise) {
        this.group = group;
        this.sender = sender;
        this.debtor = debtor;
        this.amountPaise = amountPaise;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
