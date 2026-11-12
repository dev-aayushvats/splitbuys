package com.splitbuys.backend.ledger;

import java.util.List;

/**
 * What the signed-in user's own shares came to, month by month (oldest first, the last entry is the current month, in IST),
 * across the groups they are in now. A month with no spending is still listed, with a zero total.
 */
public record SpendingInsights(List<MonthSpending> months) {
    public record MonthSpending(String month, long totalPaise, List<CategoryAmount> categories) {
    }

    /** One category's part of a month, biggest first within the month. */
    public record CategoryAmount(ExpenseCategory category, long paise) {
    }
}
