package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
class ExpenseShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Expense expense;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    private long amountPaise;

    protected ExpenseShare() {
    }

    ExpenseShare(Expense expense, AppUser user, long amountPaise) {
        this.expense = expense;
        this.user = user;
        this.amountPaise = amountPaise;
    }

    AppUser getUser() {
        return user;
    }

    long getAmountPaise() {
        return amountPaise;
    }
}
