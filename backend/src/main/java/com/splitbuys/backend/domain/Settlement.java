package com.splitbuys.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** A real-world payment from one member to another, recorded to cancel out debt. */
@Entity
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public ExpenseGroup group;

    @ManyToOne(optional = false)
    public AppUser fromUser;

    @ManyToOne(optional = false)
    public AppUser toUser;

    public long amountPaise;

    @ManyToOne(optional = false)
    public AppUser createdBy;

    public Instant createdAt = Instant.now();
}
