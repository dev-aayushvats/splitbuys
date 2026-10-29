package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.common.error.NotFoundException;
import com.splitbuys.backend.group.ExpenseGroup;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.split.SplitCalculator;
import com.splitbuys.backend.split.SplitType;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    LedgerService(GroupService groups, UserService users, ExpenseRepository expenses, SettlementRepository settlements) {
        this.groups = groups;
        this.users = users;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> listExpenses(long groupId, long userId) {
        groups.requireMember(groupId, userId);
        return expenses.findByGroup_IdOrderByCreatedAtDescIdDesc(groupId).stream().map(ExpenseDto::from).toList();
    }

    public ExpenseDto addExpense(long groupId, long userId, ExpenseRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        Expense expense = new Expense(group, users.require(userId));
        revise(expense, group, req);
        return ExpenseDto.from(expenses.save(expense));
    }

    public ExpenseDto updateExpense(long groupId, long expenseId, long userId, ExpenseRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        Expense expense = expense(groupId, expenseId);
        revise(expense, group, req);
        return ExpenseDto.from(expense);
    }

    public void deleteExpense(long groupId, long expenseId, long userId) {
        groups.requireMember(groupId, userId);
        expenses.delete(expense(groupId, expenseId));
    }

    @Transactional(readOnly = true)
    public List<SettlementDto> listSettlements(long groupId, long userId) {
        groups.requireMember(groupId, userId);
        return settlements.findByGroup_IdOrderByCreatedAtDescIdDesc(groupId).stream().map(SettlementDto::from).toList();
    }

    public SettlementDto addSettlement(long groupId, long userId, SettlementRequest req) {
        ExpenseGroup group = groups.requireMember(groupId, userId);
        if (req.fromUserId().equals(req.toUserId())) {
            throw new InvalidRequestException("Payer and receiver must be different people");
        }
        Settlement s = new Settlement(group, member(group, req.fromUserId()), member(group, req.toUserId()),
                req.amountPaise(), users.require(userId));
        return SettlementDto.from(settlements.save(s));
    }

    public void deleteSettlement(long groupId, long settlementId, long userId) {
        groups.requireMember(groupId, userId);
        settlements.delete(settlements.findByIdAndGroup_Id(settlementId, groupId)
                .orElseThrow(() -> new NotFoundException("Settlement not found")));
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
        expense.revise(req.description(), req.amountPaise(), member(group, req.payerId()), req.splitType(), owed);
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
