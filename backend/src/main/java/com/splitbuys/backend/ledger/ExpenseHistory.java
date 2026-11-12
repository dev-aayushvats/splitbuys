package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** One change to an expense. Append-only, except that a delete remembers which expense its undo created. */
@Entity
class ExpenseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpenseGroup group;

    /** Not a foreign key: this row must survive the deletion of the expense it describes. */
    private long expenseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HistoryAction action;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser actor;

    private Instant createdAt = Instant.now();

    @Column(length = 16384)
    private String beforeJson;

    @Column(length = 16384)
    private String afterJson;

    /** For a DELETED row: the id of the expense its undo created, so it cannot be restored twice. */
    private Long restoredAs;

    protected ExpenseHistory() {
    }

    ExpenseHistory(ExpenseGroup group, long expenseId, HistoryAction action, AppUser actor, String beforeJson, String afterJson) {
        this.group = group;
        this.expenseId = expenseId;
        this.action = action;
        this.actor = actor;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    Long getId() {
        return id;
    }

    long getExpenseId() {
        return expenseId;
    }

    HistoryAction getAction() {
        return action;
    }

    AppUser getActor() {
        return actor;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getBeforeJson() {
        return beforeJson;
    }

    String getAfterJson() {
        return afterJson;
    }

    Long getRestoredAs() {
        return restoredAs;
    }

    void markRestoredAs(long expenseId) {
        this.restoredAs = expenseId;
    }
}
