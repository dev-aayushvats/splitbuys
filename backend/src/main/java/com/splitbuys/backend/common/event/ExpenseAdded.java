package com.splitbuys.backend.common.event;

import java.util.List;
import java.util.Map;

/**
 * Published (inside the transaction that saved it) when someone adds an expense. Carries plain values only, so whoever
 * listens, for example push notifications, needs no knowledge of the ledger.
 *
 * @param shares       what each participant owes, keyed by user id
 * @param memberIds    everyone in the group when the expense was added, the actor included
 */
public record ExpenseAdded(long groupId, String groupName, long actorId, String actorName, long expenseId, String description,
                           long amountPaise, long payerId, Map<Long, Long> shares, List<Long> memberIds) {
}
