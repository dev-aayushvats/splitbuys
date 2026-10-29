package com.splitbuys.backend.service;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.domain.Expense;
import com.splitbuys.backend.domain.ExpenseGroup;
import com.splitbuys.backend.domain.ExpenseShare;
import com.splitbuys.backend.domain.Settlement;
import com.splitbuys.backend.repo.Repos.ExpenseRepository;
import com.splitbuys.backend.repo.Repos.SettlementRepository;
import com.splitbuys.backend.split.SplitCalculator;
import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.web.ApiException;
import com.splitbuys.backend.web.Dto.ExpenseDto;
import com.splitbuys.backend.web.Dto.ExpenseRequest;
import com.splitbuys.backend.web.Dto.SettlementDto;
import com.splitbuys.backend.web.Dto.SettlementRequest;
import com.splitbuys.backend.web.Dto.SplitInput;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Expenses and settlements of a group. Any member may add, edit or delete. */
@Service
@Transactional
public class LedgerService {
    private final GroupService groups;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;

    public LedgerService(GroupService groups, ExpenseRepository expenses, SettlementRepository settlements) {
        this.groups = groups;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> listExpenses(long groupId, long userId) {
        groups.requireMemberGroup(groupId, userId);
        return expenses.findByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream().map(Mappers::expense).toList();
    }

    public ExpenseDto addExpense(long groupId, long userId, ExpenseRequest req) {
        ExpenseGroup g = groups.requireMemberGroup(groupId, userId);
        Expense e = new Expense();
        e.group = g;
        e.createdBy = groups.user(userId);
        apply(e, g, req);
        return Mappers.expense(expenses.save(e));
    }

    public ExpenseDto updateExpense(long groupId, long expenseId, long userId, ExpenseRequest req) {
        ExpenseGroup g = groups.requireMemberGroup(groupId, userId);
        Expense e = expense(g, expenseId);
        apply(e, g, req);
        return Mappers.expense(e);
    }

    public void deleteExpense(long groupId, long expenseId, long userId) {
        ExpenseGroup g = groups.requireMemberGroup(groupId, userId);
        expenses.delete(expense(g, expenseId));
    }

    @Transactional(readOnly = true)
    public List<SettlementDto> listSettlements(long groupId, long userId) {
        groups.requireMemberGroup(groupId, userId);
        return settlements.findByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream().map(Mappers::settlement).toList();
    }

    public SettlementDto addSettlement(long groupId, long userId, SettlementRequest req) {
        ExpenseGroup g = groups.requireMemberGroup(groupId, userId);
        if (req.fromUserId().equals(req.toUserId())) {
            throw ApiException.badRequest("Payer and receiver must be different people");
        }
        Settlement s = new Settlement();
        s.group = g;
        s.fromUser = member(g, req.fromUserId());
        s.toUser = member(g, req.toUserId());
        s.amountPaise = req.amountPaise();
        s.createdBy = groups.user(userId);
        return Mappers.settlement(settlements.save(s));
    }

    public void deleteSettlement(long groupId, long settlementId, long userId) {
        ExpenseGroup g = groups.requireMemberGroup(groupId, userId);
        Settlement s = settlements.findById(settlementId)
                .filter(x -> x.group.id.equals(g.id))
                .orElseThrow(() -> ApiException.notFound("Settlement not found"));
        settlements.delete(s);
    }

    private Expense expense(ExpenseGroup g, long expenseId) {
        return expenses.findById(expenseId)
                .filter(x -> x.group.id.equals(g.id))
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
    }

    private AppUser member(ExpenseGroup g, long userId) {
        return g.members.stream().filter(u -> u.id == userId).findFirst()
                .orElseThrow(() -> ApiException.badRequest("User " + userId + " is not in this group"));
    }

    private void apply(Expense e, ExpenseGroup g, ExpenseRequest req) {
        e.description = req.description().trim();
        e.amountPaise = req.amountPaise();
        e.payer = member(g, req.payerId());
        e.splitType = req.splitType();

        Map<Long, Long> values = new LinkedHashMap<>();
        for (SplitInput in : req.splits()) {
            member(g, in.userId());
            if (values.put(in.userId(), toRaw(req.splitType(), in.value())) != null) {
                throw ApiException.badRequest("Each person can appear only once in a split");
            }
        }
        Map<Long, Long> owed = SplitCalculator.calculate(req.splitType(), req.amountPaise(), values);

        e.shares.clear();
        owed.forEach((userId, paise) -> {
            ExpenseShare s = new ExpenseShare();
            s.expense = e;
            s.user = member(g, userId);
            s.amountPaise = paise;
            e.shares.add(s);
        });
    }

    /** Converts the client's value into the unit SplitCalculator expects (paise / basis points / weight). */
    private static long toRaw(SplitType type, BigDecimal value) {
        if (type == SplitType.EQUAL) {
            return 0;
        }
        if (value == null) {
            throw ApiException.badRequest("A value is required for every person in this split type");
        }
        try {
            return (type == SplitType.PERCENT ? value.movePointRight(2) : value).longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest(type == SplitType.PERCENT
                    ? "Percentages can have at most 2 decimal places"
                    : "Values must be whole numbers");
        }
    }
}
