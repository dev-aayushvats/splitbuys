package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.group.GroupService;
import com.splitbuys.backend.user.AppUser;
import com.splitbuys.backend.user.UserDto;
import com.splitbuys.backend.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
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
    private final UserService users;

    InsightsService(GroupService groups, ExpenseRepository expenses, Clock clock, UserService users) {
        this.users = users;
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

    static final int DETAIL_LIMIT = 5;
    /** How far back a month can be asked for. */
    static final int MAX_MONTHS_BACK = 24;

    /** One month in detail. {@code month} is {@code yyyy-MM}, in IST; a future month or one more than two years back is refused. */
    public MonthDetail month(long userId, String month) {
        YearMonth wanted;
        try {
            wanted = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Month must look like 2026-11");
        }
        YearMonth now = YearMonth.from(Instant.now(clock).atZone(IstMonths.IST));
        if (wanted.isAfter(now) || wanted.isBefore(now.minusMonths(MAX_MONTHS_BACK))) {
            throw new InvalidRequestException("Month must be within the last " + MAX_MONTHS_BACK + " months");
        }
        List<Instant> b = IstMonths.boundaries(List.of(wanted));
        Instant from = b.get(0), to = b.get(1);
        int days = wanted.equals(now) ? Instant.now(clock).atZone(IstMonths.IST).getDayOfMonth() : wanted.lengthOfMonth();

        List<Long> groupIds = groups.groupIdsOf(userId);
        if (groupIds.isEmpty()) {
            return new MonthDetail(wanted.toString(), 0, 0, 0, days, List.of(), List.of());
        }
        Object[] total = expenses.myShareTotal(userId, groupIds, from, to).get(0);
        long paid = expenses.paidTotal(userId, groupIds, from, to);
        List<MonthDetail.GroupAmount> byGroup = expenses.myShareByGroup(userId, groupIds, from, to, PageRequest.ofSize(DETAIL_LIMIT)).stream()
                .map(r -> new MonthDetail.GroupAmount(((Number) r[0]).longValue(), (String) r[1], ((Number) r[2]).longValue())).toList();
        List<Object[]> withRows = expenses.myShareWithPeople(userId, groupIds, from, to, PageRequest.ofSize(DETAIL_LIMIT));
        Map<Long, AppUser> found = users.byIds(withRows.stream().map(r -> ((Number) r[0]).longValue()).toList());
        List<MonthDetail.PersonAmount> people = withRows.stream()
                .map(r -> new MonthDetail.PersonAmount(UserDto.from(found.get(((Number) r[0]).longValue())), ((Number) r[1]).longValue())).toList();
        return new MonthDetail(wanted.toString(), ((Number) total[0]).longValue(), paid, ((Number) total[1]).longValue(), days, byGroup, people);
    }
}
