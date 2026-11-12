package com.splitbuys.backend.ledger;

import com.splitbuys.backend.split.SplitType;
import java.util.Comparator;
import java.util.List;

/**
 * Everything an expense says about who paid and who owes what, as plain values. Stored as JSON in the history so an edit or a
 * delete can be shown and undone. Shares are sorted by person so two snapshots of the same state compare equal.
 */
record ExpenseSnapshot(String description, long amountPaise, long payerId, SplitType splitType, List<Share> shares) {
    record Share(long userId, long amountPaise) {
    }

    static ExpenseSnapshot of(Expense e) {
        List<Share> shares = e.getShares().stream()
                .map(s -> new Share(s.getUser().getId(), s.getAmountPaise()))
                .sorted(Comparator.comparingLong(Share::userId)).toList();
        return new ExpenseSnapshot(e.getDescription(), e.getAmountPaise(), e.getPayer().getId(), e.getSplitType(), shares);
    }
}
