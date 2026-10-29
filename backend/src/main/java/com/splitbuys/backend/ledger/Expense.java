package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity
class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpenseGroup group;

    private String description;
    private long amountPaise;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser payer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser createdBy;

    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private SplitType splitType;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {
    }

    Expense(ExpenseGroup group, AppUser createdBy) {
        this.group = group;
        this.createdBy = createdBy;
    }

    Long getId() {
        return id;
    }

    String getDescription() {
        return description;
    }

    long getAmountPaise() {
        return amountPaise;
    }

    AppUser getPayer() {
        return payer;
    }

    AppUser getCreatedBy() {
        return createdBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    SplitType getSplitType() {
        return splitType;
    }

    List<ExpenseShare> getShares() {
        return Collections.unmodifiableList(shares);
    }

    /** Replaces everything the expense says about who paid and who owes what. */
    void revise(String description, long amountPaise, AppUser payer, SplitType splitType, Map<AppUser, Long> owed) {
        this.description = description.trim();
        this.amountPaise = amountPaise;
        this.payer = payer;
        this.splitType = splitType;
        this.shares.clear();
        owed.forEach((user, paise) -> this.shares.add(new ExpenseShare(this, user, paise)));
    }
}
