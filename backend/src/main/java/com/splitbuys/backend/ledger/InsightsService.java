package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.GroupService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's own spending over the last six months: one aggregate query however many expenses there are. */
@Service
@Transactional(readOnly = true)
public class InsightsService {
    static final int MONTHS = 6;

    private final GroupService groups;
    private final ExpenseRepository expenses;
    private final Clock clock;

    InsightsService(GroupService groups, ExpenseRepository expenses, Clock clock) {
        this.groups = groups;
        this.expenses = expenses;
        this.clock = clock;
    }

    public SpendingInsights spending(long userId) {
        List<YearMonth> months = IstMonths.lastMonths(Instant.now(clock), MONTHS);
        List<Instant> b = IstMonths.boundaries(months);
        List<Long> groupIds = groups.groupIdsOf(userId);

        List<List<SpendingInsights.CategoryAmount>> perMonth = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            perMonth.add(new ArrayList<>());
        }
        if (!groupIds.isEmpty()) {
            for (Object[] row : expenses.myShareByMonth(userId, groupIds, b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5), b.get(6))) {
                ExpenseCategory category = (ExpenseCategory) row[0];
                for (int i = 0; i < MONTHS; i++) {
                    long paise = ((Number) row[i + 1]).longValue();
                    if (paise > 0) {
                        perMonth.get(i).add(new SpendingInsights.CategoryAmount(category, paise));
                    }
                }
            }
        }
        List<SpendingInsights.MonthSpending> out = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            List<SpendingInsights.CategoryAmount> cats = perMonth.get(i).stream()
                    .sorted(Comparator.comparingLong(SpendingInsights.CategoryAmount::paise).reversed()).toList();
            out.add(new SpendingInsights.MonthSpending(months.get(i).toString(), cats.stream().mapToLong(SpendingInsights.CategoryAmount::paise).sum(), cats));
        }
        return new SpendingInsights(out);
    }
}
