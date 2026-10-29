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

/** A real-world payment from one member to another, recorded to cancel out debt. */
@Entity
class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpenseGroup group;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser fromUser;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser toUser;

    private long amountPaise;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser createdBy;

    private Instant createdAt = Instant.now();

    protected Settlement() {
    }

    Settlement(ExpenseGroup group, AppUser from, AppUser to, long amountPaise, AppUser createdBy) {
        this.group = group;
        this.fromUser = from;
        this.toUser = to;
        this.amountPaise = amountPaise;
        this.createdBy = createdBy;
    }

    Long getId() {
        return id;
    }

    AppUser getFromUser() {
        return fromUser;
    }

    AppUser getToUser() {
        return toUser;
    }

    long getAmountPaise() {
        return amountPaise;
    }

    AppUser getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
