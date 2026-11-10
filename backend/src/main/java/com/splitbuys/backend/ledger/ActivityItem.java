package com.splitbuys.backend.ledger;

import java.time.Instant;

/**
 * One line of the activity feed: an expense or a payment in one of the user's groups. Exactly one of {@code expense} and
 * {@code payment} is set, matching {@code type}. For an expense, {@code myNetPaise} is what it did to the user's balance:
 * positive if they paid more than their share (they are owed), negative if they owe. Payments leave it null, because the
 * direction is in {@code payment.fromUser} / {@code payment.toUser}.
 */
public record ActivityItem(Type type, long id, long groupId, String groupName, Instant createdAt,
                           ExpenseDto expense, SettlementDto payment, Long myNetPaise) {
    /** Declaration order is the tie-break order for rows with the same timestamp (see {@link ActivityCursor}). */
    public enum Type {
        EXPENSE, PAYMENT
    }
}
