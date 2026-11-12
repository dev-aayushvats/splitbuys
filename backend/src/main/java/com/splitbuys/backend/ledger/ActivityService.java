package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.web.PageCursor;
import com.splitbuys.backend.common.web.PageResponse;
import com.splitbuys.backend.group.GroupService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything that involves the user, across their groups, newest first: expenses they paid for or share in, and payments
 * they made or received. Only groups they are in now count, so leaving a group takes its history out of the feed.
 *
 * <p>Two tables feed one list, so a page is built in three steps: take one page of (id, time) pairs from each table, merge
 * them and keep the first {@code limit}, then load full rows for just those. Each table is asked for {@code limit + 1}
 * rows, which is enough because the merged page can take at most that many from either one, and the extra row tells us
 * whether there is a next page.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {
    private static final Comparator<Entry> NEWEST_FIRST = Comparator.comparing((Entry e) -> e.stamp().createdAt())
            .thenComparing(e -> e.type().ordinal()).thenComparingLong(e -> e.stamp().id()).reversed();

    private record Entry(ActivityItem.Type type, Stamp stamp) {
    }

    private final GroupService groups;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;

    ActivityService(GroupService groups, ExpenseRepository expenses, SettlementRepository settlements) {
        this.groups = groups;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    /** With a text or category filter only expenses can match: a payment has neither a description nor a category, so none are listed. */
    public PageResponse<ActivityItem> list(long userId, int limit, String cursor, String text, ExpenseCategory category) {
        PageCursor.checkLimit(limit);
        ExpenseFilter f = ExpenseFilter.of(text, category);
        ActivityCursor from = ActivityCursor.decode(cursor);
        List<Long> groupIds = groups.groupIdsOf(userId);
        if (groupIds.isEmpty()) {
            return new PageResponse<>(List.of(), null);
        }
        Pageable size = PageRequest.ofSize(limit + 1);

        List<Entry> merged = new ArrayList<>();
        (from == null ? expenses.involvingFirst(groupIds, userId, f.pattern(), f.categories(), size)
                : expenses.involvingAfter(groupIds, userId, f.pattern(), f.categories(), from.at(), from.idBoundFor(ActivityItem.Type.EXPENSE), size))
                .forEach(s -> merged.add(new Entry(ActivityItem.Type.EXPENSE, s)));
        if (!f.active()) {
            (from == null ? settlements.involvingFirst(groupIds, userId, size)
                    : settlements.involvingAfter(groupIds, userId, from.at(), from.idBoundFor(ActivityItem.Type.PAYMENT), size))
                    .forEach(s -> merged.add(new Entry(ActivityItem.Type.PAYMENT, s)));
        }
        merged.sort(NEWEST_FIRST);

        boolean more = merged.size() > limit;
        List<Entry> page = more ? merged.subList(0, limit) : merged;

        Map<Long, Expense> expenseRows = load(page, ActivityItem.Type.EXPENSE, expenses::findForActivityByIdIn, Expense::getId);
        Map<Long, Settlement> paymentRows = load(page, ActivityItem.Type.PAYMENT, settlements::findForActivityByIdIn, Settlement::getId);
        List<ActivityItem> items = page.stream()
                .map(e -> e.type() == ActivityItem.Type.EXPENSE
                        ? expenseItem(expenseRows.get(e.stamp().id()), userId)
                        : paymentItem(paymentRows.get(e.stamp().id())))
                .toList();

        Entry last = page.isEmpty() ? null : page.get(page.size() - 1);
        String next = more ? new ActivityCursor(last.stamp().createdAt(), last.type(), last.stamp().id()).encode() : null;
        return new PageResponse<>(items, next);
    }

    private static <T> Map<Long, T> load(List<Entry> page, ActivityItem.Type type,
                                         Function<List<Long>, List<T>> loader, Function<T, Long> idOf) {
        List<Long> ids = page.stream().filter(e -> e.type() == type).map(e -> e.stamp().id()).toList();
        Map<Long, T> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            loader.apply(ids).forEach(r -> byId.put(idOf.apply(r), r));
        }
        return byId;
    }

    private static ActivityItem expenseItem(Expense e, long userId) {
        long paid = e.getPayer().getId() == userId ? e.getAmountPaise() : 0;
        long share = e.getShares().stream().filter(s -> s.getUser().getId() == userId).mapToLong(ExpenseShare::getAmountPaise).sum();
        return new ActivityItem(ActivityItem.Type.EXPENSE, e.getId(), e.getGroup().getId(), e.getGroup().getName(),
                e.getCreatedAt(), ExpenseDto.from(e), null, paid - share);
    }

    private static ActivityItem paymentItem(Settlement s) {
        return new ActivityItem(ActivityItem.Type.PAYMENT, s.getId(), s.getGroup().getId(), s.getGroup().getName(),
                s.getCreatedAt(), null, SettlementDto.from(s), null);
    }
}
