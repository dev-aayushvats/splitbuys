package com.splitbuys.backend.group;

import java.util.Collection;
import java.util.Map;

/**
 * What the group feature needs from the ledger. Declared here and implemented by the ledger feature, so the
 * dependency points one way (ledger → group) and the two never form a cycle.
 */
public interface LedgerPort {
    /** Net paise per user for each group: positive = owed money, negative = owes money. Absent users are settled. */
    Map<Long, Map<Long, Long>> netBalances(Collection<Long> groupIds);

    /** Erases all expenses and payments of a group (used when the group itself is deleted). */
    void purge(long groupId);
}
