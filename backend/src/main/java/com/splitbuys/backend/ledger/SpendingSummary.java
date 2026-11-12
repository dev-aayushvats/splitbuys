package com.splitbuys.backend.ledger;

import java.util.List;

/** Where a group's money went, biggest category first. Categories with no spending are left out. */
public record SpendingSummary(long totalPaise, long mySharePaise, List<CategoryTotal> categories) {
}
