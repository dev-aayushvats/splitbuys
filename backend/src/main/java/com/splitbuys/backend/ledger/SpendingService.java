package com.splitbuys.backend.ledger;

import com.splitbuys.backend.group.GroupService;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spending by category for one group: two aggregate queries however many expenses there are. */
@Service
@Transactional(readOnly = true)
public class SpendingService {
    private final GroupService groups;
    private final ExpenseRepository expenses;

    SpendingService(GroupService groups, ExpenseRepository expenses) {
        this.groups = groups;
        this.expenses = expenses;
    }

    public SpendingSummary summary(long groupId, long userId) {
        groups.requireMember(groupId, userId);
        Map<ExpenseCategory, Long> mine = new EnumMap<>(ExpenseCategory.class);
        for (Object[] row : expenses.myShareByCategory(groupId, userId)) {
            mine.put((ExpenseCategory) row[0], ((Number) row[1]).longValue());
        }
        List<CategoryTotal> categories = expenses.totalsByCategory(groupId).stream()
                .map(r -> new CategoryTotal((ExpenseCategory) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                        mine.getOrDefault((ExpenseCategory) r[0], 0L)))
                .sorted(Comparator.comparingLong(CategoryTotal::totalPaise).reversed())
                .toList();
        return new SpendingSummary(categories.stream().mapToLong(CategoryTotal::totalPaise).sum(),
                categories.stream().mapToLong(CategoryTotal::mySharePaise).sum(), categories);
    }
}
