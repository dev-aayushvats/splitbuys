package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.common.idempotency.IdempotencyService;
import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.split.SplitCalculator;
import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserService;
import com.splitbuys.backend.common.web.PageCursor;
import com.splitbuys.backend.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import com.splitbuys.backend.common.event.ExpenseAdded;
import com.splitbuys.backend.common.event.PaymentRecorded;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expenses and payments of a group. Any member may add, edit or delete. */
@Service
@Transactional
public class LedgerService {
    private final GroupService groups;
    private final UserService users;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final ExpenseHistoryRepository historyRows;
    private final HistoryLog history;

    LedgerService(GroupService groups, UserService users, ExpenseRepository expenses, SettlementRepository settlements,
                  IdempotencyService idempotency, ApplicationEventPublisher events, ExpenseHistoryRepository historyRows,
                  HistoryLog history) {
        this.historyRows = historyRows;
        this.history = history;
        this.idempotency = idempotency;
        this.events = events;
        this.groups = groups;
        this.users = users;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseDto> listExpenses(long groupId, long userId, int limit, String cursor) {
        groups.requireMember(groupId, userId);
        return page(limit, cursor,
                size -> expenses.firstPageIds(groupId, size),
                (c, size) -> expenses.idsAfter(groupId, c.createdAt(), c.id(), size),
                expenses::findByIdIn, Expense::getId, Expense::getCreatedAt, ExpenseDto::from);
    }

    @Transactional(readOnly = true)
    public ExpenseDto getExpense(long groupId, long expenseId, long userId) {
        groups.requireMember(groupId, userId);
        return ExpenseDto.from(expense(groupId, expenseId));
    }

    public ExpenseDto addExpense(long groupId, long userId, String idempotencyKey, ExpenseRequest req) {
        return idempotency.once(userId, idempotencyKey, "expense:" + groupId, req, ExpenseDto.class,
                () -> addExpense(groupId, userId, req));
    }

    private ExpenseDto addExpense(long groupId, long userId, ExpenseRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        AppUser actor = users.require(userId);
        Expense expense = new Expense(group, actor);
        revise(expense, group, req);
        Expense persisted = expenses.save(expense);
        history.record(group, persisted.getId(), HistoryAction.CREATED, actor, null, ExpenseSnapshot.of(persisted));
        ExpenseDto saved = ExpenseDto.from(persisted);
        Map<Long, Long> shares = new LinkedHashMap<>();
        saved.shares().forEach(s -> shares.put(s.user().id(), s.amountPaise()));
        events.publishEvent(new ExpenseAdded(groupId, group.getName(), userId, label(actor), saved.id(), saved.description(),
                saved.amountPaise(), saved.payer().id(), shares, group.getMembers().stream().map(AppUser::getId).toList()));
        return saved;
    }

    public ExpenseDto updateExpense(long groupId, long expenseId, long userId, ExpenseRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        Expense expense = expense(groupId, expenseId);
        ExpenseSnapshot before = ExpenseSnapshot.of(expense);
        revise(expense, group, req);
        ExpenseSnapshot after = ExpenseSnapshot.of(expense);
        if (!before.equals(after)) { // saving without changing anything is not worth a history entry
            history.record(group, expenseId, HistoryAction.EDITED, users.require(userId), before, after);
        }
        return ExpenseDto.from(expense);
    }

    public void deleteExpense(long groupId, long expenseId, long userId) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        Expense expense = expense(groupId, expenseId);
        history.record(group, expenseId, HistoryAction.DELETED, users.require(userId), ExpenseSnapshot.of(expense), null);
        expenses.delete(expense);
    }

    /** Every change to the group's expenses, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<HistoryDto> listHistory(long groupId, long userId, int limit, String cursor) {
        groups.requireMember(groupId, userId);
        PageResponse<ExpenseHistory> rows = page(limit, cursor,
                size -> historyRows.firstPageIds(groupId, size),
                (c, size) -> historyRows.idsAfter(groupId, c.createdAt(), c.id(), size),
                historyRows::findByIdIn, ExpenseHistory::getId, ExpenseHistory::getCreatedAt, Function.identity());
        return new PageResponse<>(describe(groupId, rows.items()), rows.nextCursor());
    }

    /** The changes to one expense (at most the newest 50), including an expense that has since been deleted. */
    @Transactional(readOnly = true)
    public List<HistoryDto> expenseHistory(long groupId, long expenseId, long userId) {
        groups.requireMember(groupId, userId);
        return describe(groupId, historyRows.findTop50ByGroup_IdAndExpenseIdOrderByIdDesc(groupId, expenseId));
    }

    private List<HistoryDto> describe(long groupId, List<ExpenseHistory> rows) {
        Set<Long> live = new HashSet<>();
        expenses.findAllById(rows.stream().map(ExpenseHistory::getExpenseId).distinct().toList())
                .forEach(e -> live.add(e.getId()));
        return history.toDtos(groupId, rows, live);
    }

    /**
     * Undoes one change. An edit can be undone only while it is still the latest change to that expense (otherwise the
     * undo would silently throw away a later edit); a deletion can be undone once, which creates the expense again.
     */
    public HistoryDto undo(long groupId, long historyId, long userId) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        AppUser actor = users.require(userId);
        ExpenseHistory row = historyRows.findByIdAndGroup_Id(historyId, groupId)
                .orElseThrow(() -> new NotFoundException("History entry not found"));
        ExpenseHistory made;
        switch (row.getAction()) {
            case EDITED -> {
                Expense expense = expenses.findByIdAndGroup_Id(row.getExpenseId(), groupId).orElseThrow(
                        () -> new ConflictException("EXPENSE_GONE", "This expense has since been deleted"));
                ExpenseHistory newest = historyRows.findTop50ByGroup_IdAndExpenseIdOrderByIdDesc(groupId, row.getExpenseId()).get(0);
                if (!newest.getId().equals(row.getId())) {
                    throw new ConflictException("CHANGED_SINCE", "This expense was changed again after this edit, so it cannot be undone");
                }
                ExpenseSnapshot target = HistoryLog.before(row);
                ExpenseSnapshot current = ExpenseSnapshot.of(expense);
                apply(expense, group, target);
                made = history.record(group, expense.getId(), HistoryAction.REVERTED, actor, current, target);
            }
            case DELETED -> {
                if (row.getRestoredAs() != null) {
                    throw new ConflictException("ALREADY_RESTORED", "This expense has already been restored");
                }
                ExpenseSnapshot target = HistoryLog.before(row);
                Expense expense = new Expense(group, actor);
                apply(expense, group, target);
                Expense saved = expenses.save(expense);
                row.markRestoredAs(saved.getId());
                made = history.record(group, saved.getId(), HistoryAction.RESTORED, actor, null, ExpenseSnapshot.of(saved));
            }
            default -> throw new InvalidRequestException("Only an edit or a deletion can be undone");
        }
        return history.toDtos(groupId, List.of(made), Set.of(made.getExpenseId())).get(0);
    }

    /** Puts an expense back to a stored state, provided everyone in it is still in the group. */
    private static void apply(Expense expense, ExpenseGroup group, ExpenseSnapshot s) {
        Function<Long, AppUser> who = id -> group.findMember(id).orElseThrow(() -> new ConflictException("MEMBER_LEFT",
                "Someone in this expense has left the group, so it cannot be restored"));
        Map<AppUser, Long> owed = new LinkedHashMap<>();
        s.shares().forEach(sh -> owed.put(who.apply(sh.userId()), sh.amountPaise()));
        expense.revise(s.description(), s.amountPaise(), who.apply(s.payerId()), s.splitType(), s.category(), owed);
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementDto> listSettlements(long groupId, long userId, int limit, String cursor) {
        groups.requireMember(groupId, userId);
        return page(limit, cursor,
                size -> settlements.firstPageIds(groupId, size),
                (c, size) -> settlements.idsAfter(groupId, c.createdAt(), c.id(), size),
                settlements::findByIdIn, Settlement::getId, Settlement::getCreatedAt, SettlementDto::from);
    }

    /**
     * Keyset pagination shared by expenses and payments: ask for one row more than the page to learn whether there is a
     * next page, load full rows for just the page's ids, and keep the SQL ordering.
     */
    private static <E, D> PageResponse<D> page(int limit, String cursor,
                                               Function<Pageable, List<Long>> first,
                                               BiFunction<PageCursor, Pageable, List<Long>> after,
                                               Function<Collection<Long>, List<E>> load,
                                               Function<E, Long> idOf, Function<E, Instant> atOf, Function<E, D> dto) {
        Pageable size = PageRequest.ofSize(PageCursor.checkLimit(limit) + 1);
        PageCursor from = PageCursor.decode(cursor);
        List<Long> ids = from == null ? first.apply(size) : after.apply(from, size);
        boolean more = ids.size() > limit;
        List<Long> pageIds = more ? ids.subList(0, limit) : ids;
        Map<Long, E> byId = new HashMap<>();
        load.apply(pageIds).forEach(e -> byId.put(idOf.apply(e), e));
        List<E> rows = pageIds.stream().map(byId::get).toList();
        E last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        String next = more ? new PageCursor(atOf.apply(last), idOf.apply(last)).encode() : null;
        return new PageResponse<>(rows.stream().map(dto).toList(), next);
    }

    public SettlementDto addSettlement(long groupId, long userId, String idempotencyKey, SettlementRequest req) {
        return idempotency.once(userId, idempotencyKey, "settlement:" + groupId, req, SettlementDto.class,
                () -> addSettlement(groupId, userId, req));
    }

    private SettlementDto addSettlement(long groupId, long userId, SettlementRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        if (req.fromUserId().equals(req.toUserId())) {
            throw new InvalidRequestException("Payer and receiver must be different people");
        }
        AppUser actor = users.require(userId);
        AppUser from = member(group, req.fromUserId());
        AppUser to = member(group, req.toUserId());
        SettlementDto saved = SettlementDto.from(settlements.save(new Settlement(group, from, to, req.amountPaise(), actor)));
        events.publishEvent(new PaymentRecorded(groupId, group.getName(), userId, label(actor), from.getId(), label(from),
                to.getId(), label(to), saved.amountPaise()));
        return saved;
    }

    public void deleteSettlement(long groupId, long settlementId, long userId) {
        groups.requireMember(groupId, userId);
        settlements.delete(settlements.findByIdAndGroup_Id(settlementId, groupId)
                .orElseThrow(() -> new NotFoundException("Settlement not found")));
    }

    private static String label(AppUser u) {
        return u.getName() == null || u.getName().isBlank() ? u.getEmail() : u.getName();
    }

    private Expense expense(long groupId, long expenseId) {
        return expenses.findByIdAndGroup_Id(expenseId, groupId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
    }

    private static AppUser member(ExpenseGroup group, long userId) {
        return group.findMember(userId)
                .orElseThrow(() -> new InvalidRequestException("User " + userId + " is not in this group"));
    }

    private void revise(Expense expense, ExpenseGroup group, ExpenseRequest req) {
        Map<Long, Long> values = new LinkedHashMap<>();
        for (SplitInput in : req.splits()) {
            member(group, in.userId());
            if (values.put(in.userId(), toRaw(req.splitType(), in.value())) != null) {
                throw new InvalidRequestException("Each person can appear only once in a split");
            }
        }
        Map<Long, Long> owedById;
        try {
            owedById = SplitCalculator.calculate(req.splitType(), req.amountPaise(), values);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage()); // the calculator's messages are written for users
        }
        Map<AppUser, Long> owed = new LinkedHashMap<>();
        owedById.forEach((id, paise) -> owed.put(member(group, id), paise));
        expense.revise(req.description(), req.amountPaise(), member(group, req.payerId()), req.splitType(), req.category(), owed);
    }

    /** Converts the client's value into the unit SplitCalculator expects (paise / basis points / weight). */
    private static long toRaw(SplitType type, BigDecimal value) {
        if (type == SplitType.EQUAL) {
            return 0;
        }
        if (value == null) {
            throw new InvalidRequestException("A value is required for every person in this split type");
        }
        try {
            return (type == SplitType.PERCENT ? value.movePointRight(2) : value).longValueExact();
        } catch (ArithmeticException ex) {
            throw new InvalidRequestException(type == SplitType.PERCENT
                    ? "Percentages can have at most 2 decimal places"
                    : "Values must be whole numbers");
        }
    }
}
